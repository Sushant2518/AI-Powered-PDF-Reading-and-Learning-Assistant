package dev.sushant.samajh.concepts;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.sushant.samajh.ai.AiService;
import dev.sushant.samajh.ai.Prompts;
import dev.sushant.samajh.ai.SourcedText;
import dev.sushant.samajh.documents.Document;
import dev.sushant.samajh.documents.DocumentRepository;
import dev.sushant.samajh.documents.Page;
import dev.sushant.samajh.documents.PageRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Step 3 of the mastery loop: Beyond the PDF. Web search on trusted sites; every statement has a source. */
@RestController
@RequestMapping("/api/concepts")
class BeyondController {

    /** {@code accessedAt} is when the sources were fetched. */
    record Beyond(String content, @JsonRawValue String sources, int hidden, OffsetDateTime accessedAt) {}

    private final AiService ai;
    private final DocumentRepository documents;
    private final PageRepository pages;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    BeyondController(AiService ai, DocumentRepository documents, PageRepository pages, JdbcClient jdbc, JsonMapper json) {
        this.ai = ai;
        this.documents = documents;
        this.pages = pages;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Takes 10–40 s on a cache miss (Claude searches several sites). Cached per concept afterwards. */
    @GetMapping("/{conceptId}/beyond")
    Beyond beyond(@PathVariable long conceptId, @RequestParam UUID documentId, @RequestParam int page,
                  @RequestParam(defaultValue = "false") boolean regenerate) {
        if (!regenerate) {
            Optional<Beyond> cached = find(conceptId);
            if (cached.isPresent()) return cached.get();
        }
        String name = jdbc.sql("SELECT name FROM concepts WHERE id = ?").param(conceptId).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Document doc = documents.findById(documentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Page p = pages.readable(documentId, page);

        String user = Prompts.render("beyond", Map.of("title", doc.getTitle(), "page", String.valueOf(page), "text", p.getText(), "name", name));
        SourcedText result = ai.webSearch("beyond", Prompts.SYSTEM, user);
        if (result.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No trusted source found for " + name + ". Nothing is shown, so nothing unverified.");
        }
        jdbc.sql("""
                        INSERT INTO beyond (concept_id, content, sources, hidden, model) VALUES (?, ?, ?::jsonb, ?, ?)
                        ON CONFLICT (concept_id) DO UPDATE SET content = excluded.content, sources = excluded.sources,
                          hidden = excluded.hidden, model = excluded.model, created_at = now()""")
                .params(conceptId, result.markdown(), json.writeValueAsString(result.sources()), result.hidden(), ai.model())
                .update();
        return find(conceptId).orElseThrow();
    }

    private Optional<Beyond> find(long conceptId) {
        return jdbc.sql("SELECT content, sources::text AS sources, hidden, created_at AS accessed_at FROM beyond WHERE concept_id = ?")
                .param(conceptId).query(Beyond.class).optional();
    }
}
