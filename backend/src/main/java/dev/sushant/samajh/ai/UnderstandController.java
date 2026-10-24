package dev.sushant.samajh.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.sushant.samajh.documents.Document;
import dev.sushant.samajh.documents.DocumentRepository;
import dev.sushant.samajh.documents.Page;
import dev.sushant.samajh.documents.PageRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Step 1 of the mastery loop: Understand. Streams over SSE (see {@link Sse}). */
@RestController
@RequestMapping("/api")
class UnderstandController {

    enum Lang {
        none(null), mr("Marathi"), hi("Hindi");
        final String label;
        Lang(String label) { this.label = label; }
    }

    enum Style {
        VERY_SIMPLE("very simple, like explaining to a 12-year-old"),
        SIMPLE("simple: short sentences, jargon explained in brackets"),
        DETAILED("detailed: add a small concrete example where it helps");
        final String description;
        Style(String description) { this.description = description; }
    }

    enum Action { EXPLAIN, TRANSLATE }

    record SelectionRequest(UUID documentId, int page, String text, Action action, Lang lang, Style style) {}

    private final AiService ai;
    private final DocumentRepository documents;
    private final PageRepository pages;
    private final JdbcTemplate jdbc;

    UnderstandController(AiService ai, DocumentRepository documents, PageRepository pages, JdbcTemplate jdbc) {
        this.ai = ai;
        this.documents = documents;
        this.pages = pages;
        this.jdbc = jdbc;
    }

    @GetMapping("/documents/{id}/pages/{n}/understand")
    SseEmitter understand(@PathVariable UUID id, @PathVariable int n,
                          @RequestParam(defaultValue = "none") Lang lang,
                          @RequestParam(defaultValue = "SIMPLE") Style style,
                          @RequestParam(defaultValue = "false") boolean regenerate) {
        Document doc = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Page page = pages.readable(id, n);

        List<String> cached = jdbc.queryForList(
                "SELECT content FROM page_ai WHERE document_id = ? AND page_number = ? AND lang = ? AND style = ?",
                String.class, id, n, lang.name(), style.name());

        return Sse.stream(send -> {
            if (!cached.isEmpty() && !regenerate) {
                send.accept(cached.getFirst());
                return;
            }
            String nativeSection = lang.label == null ? "" : """
                    <<<NATIVE>>>
                    The same explanation in natural %s (not word-for-word). Keep technical terms in English.
                    """.formatted(lang.label);
            String user = Prompts.render("understand", Map.of(
                    "title", doc.getTitle(), "page", String.valueOf(n), "text", page.getText(),
                    "style", style.description, "native", nativeSection));
            AiService.Result result = ai.stream("understand", Prompts.SYSTEM, user, OutputConfig.Effort.LOW, send);
            if ("end_turn".equals(result.stopReason())) {
                jdbc.update("""
                        INSERT INTO page_ai (document_id, page_number, lang, style, content, model) VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (document_id, page_number, lang, style)
                        DO UPDATE SET content = excluded.content, model = excluded.model, created_at = now()""",
                        id, n, lang.name(), style.name(), result.text(), ai.model());
            }
        });
    }

    @PostMapping("/ai/selection")
    SseEmitter selection(@RequestBody SelectionRequest req) {
        if (req.text() == null || req.text().isBlank() || req.text().length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select between 1 and 4000 characters");
        }
        Lang lang = req.lang() == null ? Lang.none : req.lang();
        if (req.action() == Action.TRANSLATE && lang.label == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Marathi or Hindi to translate");
        }
        Document doc = documents.findById(req.documentId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Page page = pages.readable(req.documentId(), req.page());
        String task = req.action() == Action.TRANSLATE
                ? "Explain the selected text in natural " + lang.label + " (not word-for-word). Keep technical terms in English."
                : "Explain the selected text in simple English.";
        String user = Prompts.render("selection", Map.of(
                "title", doc.getTitle(), "page", String.valueOf(req.page()), "text", page.getText(),
                "selection", req.text(), "task", task,
                "style", (req.style() == null ? Style.SIMPLE : req.style()).description));
        return Sse.stream(send -> ai.stream("selection-" + req.action().name().toLowerCase(), Prompts.SYSTEM, user, OutputConfig.Effort.LOW, send));
    }
}
