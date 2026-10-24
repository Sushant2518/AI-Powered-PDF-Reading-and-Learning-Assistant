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

    /** A piece of answer text and the sources that support it (none = unsourced). */
    public record Segment(String text, List<Source> citations) {}

    /** A Gemini grounding chunk (a web page) and a supported span of the answer. */
    public record Chunk(String uri, String title) {}

    public record Support(String text, List<Integer> chunkIndices) {}

    /** Claude: text blocks carry their own web-search citations. */
    public static SourcedText fromClaude(List<ContentBlock> content) {
        List<Segment> segments = new ArrayList<>();
        for (ContentBlock block : content) {
            block.text().ifPresent(t -> segments.add(new Segment(t.text(), t.citations().orElse(List.of()).stream()
                    .flatMap(c -> c.webSearchResultLocation().stream())
                    .map(w -> new Source(0, w.url(), w.title().orElse(w.url())))
                    .toList())));
        }
        return of(segments);
    }

    /**
     * Gemini: the answer is plain text plus "supports" (spans) pointing at grounding chunks.
     * Gemini's search can't be limited to sites, so only chunks on trusted domains count as sources here;
     * spans supported only by other sites become unsourced and are hidden like any other unsourced text.
     * Spans are located by their text (not offsets) to avoid byte-vs-character index mismatches.
     */
    public static SourcedText fromGrounding(String text, List<Chunk> chunks, List<Support> supports, List<String> trustedDomains) {
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        for (Support support : supports) {
            int at = support.text() == null || support.text().isEmpty() ? -1 : text.indexOf(support.text(), cursor);
            if (at < 0) continue;   // overlapping or not found: skip, the text stays unsourced
            if (at > cursor) segments.add(new Segment(text.substring(cursor, at), List.of()));
            List<Source> sources = support.chunkIndices().stream()
                    .filter(i -> i >= 0 && i < chunks.size())
                    .map(chunks::get)
                    .filter(c -> trusted(domain(c), trustedDomains))
                    .map(c -> new Source(0, c.uri(), domain(c)))
                    .toList();
            segments.add(new Segment(support.text(), sources));
            cursor = at + support.text().length();
        }
        if (cursor < text.length()) segments.add(new Segment(text.substring(cursor), List.of()));
        return of(segments);
    }

    /** Gemini's chunk title is the site's domain; the URI is often a redirect link. */
    private static String domain(Chunk c) {
        String title = c.title() == null ? "" : c.title().strip().toLowerCase();
        if (title.contains(".") && !title.contains(" ")) return title.replaceFirst("^www\\.", "");
        try {
            return java.net.URI.create(c.uri()).getHost().toLowerCase().replaceFirst("^www\\.", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static boolean trusted(String domain, List<String> trustedDomains) {
        return !domain.isEmpty() && trustedDomains.stream().anyMatch(t -> domain.equals(t) || domain.endsWith("." + t));
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
        String cleaned = md.toString()
                .replaceAll("(?m)[ \\t]+[-*][ \\t]*$", "")                 // bullet marker left dangling after hidden text
                .replaceAll("(?m)^[ \\t]*([-*]|\\d+\\.)[ \\t]*(\\n|$)", "")   // bullet with nothing left in it
                .replaceAll("\\n{3,}", "\\n\\n").strip();
        String markdown = dropEmptySections(cleaned);
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
