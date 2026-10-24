package dev.sushant.samajh.ai;

import com.anthropic.models.messages.ContentBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a web-search answer into markdown where every statement carries a numbered source.
 * Text without a citation is dropped (only headings and list markers survive, to keep the layout),
 * so "Beyond the PDF" never shows an unsourced claim.
 */
public record SourcedText(String markdown, List<Source> sources, int hidden) {

    public record Source(int n, String url, String title) {}

    /** One text block from Claude and the URLs it cites. */
    public record Segment(String text, List<Source> citations) {}

    public static SourcedText from(List<ContentBlock> content) {
        List<Segment> segments = new ArrayList<>();
        for (ContentBlock block : content) {
            block.text().ifPresent(t -> segments.add(new Segment(t.text(), t.citations().orElse(List.of()).stream()
                    .flatMap(c -> c.webSearchResultLocation().stream())
                    .map(w -> new Source(0, w.url(), w.title().orElse(w.url())))
                    .toList())));
        }
        return of(segments);
    }

    static SourcedText of(List<Segment> segments) {
        Map<String, Source> byUrl = new LinkedHashMap<>();
        StringBuilder md = new StringBuilder();
        int hidden = 0;
        for (Segment s : segments) {
            if (!s.citations().isEmpty()) {
                md.append(s.text().stripTrailing());
                for (String url : s.citations().stream().map(Source::url).distinct().toList()) {
                    Source src = byUrl.computeIfAbsent(url, u -> new Source(byUrl.size() + 1, u,
                            s.citations().stream().filter(c -> c.url().equals(u)).findFirst().orElseThrow().title()));
                    md.append(" [").append(src.n()).append(']');
                }
                if (s.text().endsWith("\n")) md.append('\n');
                continue;
            }
            List<String> kept = new ArrayList<>();
            String[] lines = s.text().split("\n", -1);
            for (String line : lines) {
                if (line.isBlank() || line.strip().startsWith("#") || line.matches("\\s*([-*]|\\d+\\.)\\s*")) kept.add(line);
                else hidden++;
            }
            md.append(String.join("\n", kept));
        }
        String markdown = dropEmptySections(md.toString().replaceAll("\n{3,}", "\n\n").strip());
        return new SourcedText(markdown, List.copyOf(byUrl.values()), hidden);
    }

    /** Removes headings left with nothing under them once unsourced text is gone. */
    private static String dropEmptySections(String md) {
        String[] parts = md.split("(?m)(?=^#{1,6} )");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            boolean heading = part.strip().startsWith("#");
            int newline = part.indexOf('\n');
            String body = !heading ? part : newline < 0 ? "" : part.substring(newline + 1);
            if (!heading || !body.replaceAll("[\\s*\\-\\d.]", "").isEmpty()) out.append(part);
        }
        return out.toString().strip();
    }

    /** True when there is no sourced content at all. */
    public boolean isEmpty() { return sources.isEmpty(); }
}
