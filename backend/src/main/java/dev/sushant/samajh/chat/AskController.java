package dev.sushant.samajh.chat;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.sushant.samajh.ai.AiService;
import dev.sushant.samajh.ai.Prompts;
import dev.sushant.samajh.ai.Sse;
import dev.sushant.samajh.ai.SourcedText;
import dev.sushant.samajh.documents.Document;
import dev.sushant.samajh.documents.DocumentRepository;
import dev.sushant.samajh.documents.Page;
import dev.sushant.samajh.documents.PageRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ask: chat about the PDF. The answer comes from the current page ±1 (with [p.N] markers);
 * when the pages don't cover it, a second step searches trusted sites ("Beyond the PDF").
 * Extra SSE events: {@code beyond_searching {query}}, {@code beyond {content, sources, hidden} | {error}}.
 */
@RestController
@RequestMapping("/api/documents/{id}")
class AskController {

    record AskRequest(String question, int page, String quotedText, String lang, boolean allowBeyond) {}

    record ChatMessage(long id, String role, String content, int pageNumber, String quotedText,
                       @JsonRawValue String followUps, String beyond, @JsonRawValue String sources, OffsetDateTime createdAt) {}

    /** The model's reply split into its parts (see prompts/ask.txt). */
    record Answer(String text, String beyondQuery, List<String> followUps) {
        static Answer parse(String raw) {
            String[] byFollowUps = raw.split("<<<FOLLOWUPS>>>", 2);
            String[] byBeyond = byFollowUps[0].split("<<<BEYOND>>>", 2);
            String query = byBeyond.length > 1 ? byBeyond[1].strip().lines().findFirst().orElse("").strip() : "";
            List<String> followUps = byFollowUps.length > 1
                    ? byFollowUps[1].lines().map(l -> l.replaceFirst("^\\s*([-*]|\\d+[.)])\\s*", "").strip()).filter(l -> !l.isEmpty()).limit(3).toList()
                    : List.of();
            return new Answer(byBeyond[0].strip(), query, followUps);
        }
    }

    private static final Map<String, String> NATIVE = Map.of(
            "mr", ", then repeat the answer in natural Marathi below a line containing only --- (keep technical terms in English)",
            "hi", ", then repeat the answer in natural Hindi below a line containing only --- (keep technical terms in English)");

    private final AiService ai;
    private final DocumentRepository documents;
    private final PageRepository pages;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    AskController(AiService ai, DocumentRepository documents, PageRepository pages, JdbcClient jdbc, JsonMapper json) {
        this.ai = ai;
        this.documents = documents;
        this.pages = pages;
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/chat")
    List<ChatMessage> history(@PathVariable UUID id) {
        return jdbc.sql("""
                        SELECT id, role, content, page_number, quoted_text, follow_ups::text AS follow_ups, beyond,
                               sources::text AS sources, created_at
                        FROM chat_messages WHERE document_id = ? ORDER BY id""")
                .param(id).query(ChatMessage.class).list();
    }

    @DeleteMapping("/chat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@PathVariable UUID id) {
        jdbc.sql("DELETE FROM chat_messages WHERE document_id = ?").param(id).update();
    }

    @PostMapping("/ask")
    SseEmitter ask(@PathVariable UUID id, @RequestBody AskRequest req) {
        String question = req.question() == null ? "" : req.question().strip();
        if (question.isEmpty() || question.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask a question of up to 2000 characters");
        }
        Document doc = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pages.readable(id, req.page());
        String context = IntStream.rangeClosed(req.page() - 1, req.page() + 1)
                .mapToObj(n -> pages.findByDocumentIdAndPageNumber(id, n))
                .flatMap(java.util.Optional::stream)
                .filter(p -> !p.isNeedsOcr())
                .map(p -> "<page n=\"%d\">\n%s\n</page>".formatted(p.getPageNumber(), p.getText()))
                .collect(Collectors.joining("\n"));
        String user = Prompts.render("ask", Map.of(
                "title", doc.getTitle(), "pages", context, "history", history(id, 6),
                "page", String.valueOf(req.page()),
                "quote", req.quotedText() == null || req.quotedText().isBlank() ? "" : "\nI selected this text:\n<selection>\n" + req.quotedText() + "\n</selection>",
                "question", question, "native", req.lang() == null ? "" : NATIVE.getOrDefault(req.lang(), "")));

        jdbc.sql("INSERT INTO chat_messages (document_id, role, content, page_number, quoted_text) VALUES (?, 'USER', ?, ?, ?)")
                .params(id, question, req.page(), req.quotedText()).update();

        return Sse.stream(out -> {
            Answer answer = Answer.parse(ai.stream("ask", Prompts.SYSTEM, user, AiService.Effort.LOW, out).text());
            SourcedText beyond = null;
            if (req.allowBeyond() && !answer.beyondQuery().isEmpty()) {
                out.event("beyond_searching", Map.of("query", answer.beyondQuery()));
                try {
                    beyond = ai.webSearch("ask-beyond", Prompts.SYSTEM, Prompts.render("ask-beyond", Map.of(
                            "title", doc.getTitle(), "question", question, "query", answer.beyondQuery())));
                    out.event("beyond", Map.of("content", beyond.markdown(), "sources", beyond.sources(), "hidden", beyond.hidden()));
                } catch (AiService.UserFacingException e) {
                    out.event("beyond", Map.of("error", e.getMessage()));
                }
            }
            jdbc.sql("""
                            INSERT INTO chat_messages (document_id, role, content, page_number, follow_ups, beyond, sources)
                            VALUES (?, 'ASSISTANT', ?, ?, ?::jsonb, ?, ?::jsonb)""")
                    .params(id, answer.text(), req.page(), json.writeValueAsString(answer.followUps()),
                            beyond == null || beyond.isEmpty() ? null : beyond.markdown(),
                            beyond == null || beyond.isEmpty() ? null : json.writeValueAsString(beyond.sources()))
                    .update();
        });
    }

    private String history(UUID id, int last) {
        List<String> lines = jdbc.sql("SELECT role, content FROM chat_messages WHERE document_id = ? ORDER BY id DESC LIMIT ?")
                .params(id, last).query((rs, i) -> (rs.getString(1).equals("USER") ? "Me: " : "You: ") + rs.getString(2)).list();
        if (lines.isEmpty()) return "";
        String[] ordered = lines.reversed().toArray(String[]::new);
        return "\nEarlier in this conversation:\n" + String.join("\n", Arrays.asList(ordered)) + "\n";
    }
}
