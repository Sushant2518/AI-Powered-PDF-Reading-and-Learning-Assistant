package dev.sushant.samajh.concepts;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.sushant.samajh.ai.AiService;
import dev.sushant.samajh.ai.Prompts;
import dev.sushant.samajh.ai.Sse;
import dev.sushant.samajh.documents.Document;
import dev.sushant.samajh.documents.DocumentRepository;
import dev.sushant.samajh.documents.Page;
import dev.sushant.samajh.documents.PageRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Step 2 of the mastery loop: Deepen. Concepts are extracted per page once, and deepened per concept once. */
@RestController
@RequestMapping("/api")
class ConceptController {

    enum Domain {
        SOFTWARE_ENGINEERING, BACKEND, FRONTEND, DISTRIBUTED_SYSTEMS, SECURITY,
        AI_ML, DEVOPS_CLOUD, DATA_DATABASES, CS_FUNDAMENTALS
    }

    /** Shape Claude must return when listing a page's concepts. */
    record Extracted(@JsonPropertyDescription("Standard industry name, Title Case") String name,
                     Domain domain,
                     @JsonPropertyDescription("One sentence, simple English, based on the page") String definition) {}

    record ExtractedList(List<Extracted> concepts) {}

    record Concept(long id, String name, String domain, String definition, boolean deepened) {}

    record NewConcept(UUID documentId, int page, String name) {}

    private final AiService ai;
    private final DocumentRepository documents;
    private final PageRepository pages;
    private final JdbcClient jdbc;

    ConceptController(AiService ai, DocumentRepository documents, PageRepository pages, JdbcClient jdbc) {
        this.ai = ai;
        this.documents = documents;
        this.pages = pages;
        this.jdbc = jdbc;
    }

    @GetMapping("/documents/{id}/pages/{n}/concepts")
    List<Concept> pageConcepts(@PathVariable UUID id, @PathVariable int n) {
        Document doc = document(id);
        Page page = pages.readable(id, n);
        boolean extracted = jdbc.sql("SELECT concepts_extracted FROM pages WHERE document_id = ? AND page_number = ?")
                .params(id, n).query(Boolean.class).single();
        if (!extracted) {
            String user = Prompts.render("concepts", Map.of("title", doc.getTitle(), "page", String.valueOf(n), "text", page.getText()));
            for (Extracted c : ai.structured("concepts", Prompts.SYSTEM, user, ExtractedList.class).concepts()) {
                upsert(id, n, c.name(), c.domain().name(), c.definition());
            }
            jdbc.sql("UPDATE pages SET concepts_extracted = true WHERE document_id = ? AND page_number = ?").params(id, n).update();
        }
        return jdbc.sql("""
                        SELECT c.id, c.name, c.domain, c.definition, c.deepen IS NOT NULL AS deepened
                        FROM concepts c JOIN concept_mentions m ON m.concept_id = c.id
                        WHERE m.document_id = ? AND m.page_number = ? ORDER BY c.id""")
                .params(id, n).query(Concept.class).list();
    }

    /** "Deepen" on a selected term: creates (or finds) the concept and links it to the page. */
    @PostMapping("/concepts")
    Concept create(@RequestBody NewConcept req) {
        String name = req.name() == null ? "" : req.name().strip();
        if (name.isEmpty() || name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a term (up to 80 characters) to deepen");
        }
        document(req.documentId());
        pages.readable(req.documentId(), req.page());
        long id = upsert(req.documentId(), req.page(), name, "UNSORTED", "");
        return jdbc.sql("SELECT id, name, domain, definition, deepen IS NOT NULL AS deepened FROM concepts WHERE id = ?")
                .param(id).query(Concept.class).single();
    }

    @GetMapping("/concepts/{conceptId}/deepen")
    SseEmitter deepen(@PathVariable long conceptId, @RequestParam UUID documentId, @RequestParam int page,
                      @RequestParam(defaultValue = "false") boolean regenerate) {
        Map<String, Object> concept = jdbc.sql("SELECT name, deepen FROM concepts WHERE id = ?").param(conceptId).query().singleRow();
        Document doc = document(documentId);
        Page p = pages.readable(documentId, page);
        return Sse.stream(send -> {
            if (concept.get("deepen") != null && !regenerate) {
                send.accept((String) concept.get("deepen"));
                return;
            }
            String user = Prompts.render("deepen", Map.of(
                    "title", doc.getTitle(), "page", String.valueOf(page), "text", p.getText(), "name", (String) concept.get("name")));
            AiService.Result result = ai.stream("deepen", Prompts.SYSTEM, user, AiService.Effort.MEDIUM, send);
            if ("end_turn".equals(result.stopReason())) {
                jdbc.sql("UPDATE concepts SET deepen = ?, deepen_model = ? WHERE id = ?").params(result.text(), ai.model(), conceptId).update();
            }
        });
    }

    private long upsert(UUID documentId, int page, String name, String domain, String definition) {
        long id = jdbc.sql("""
                        INSERT INTO concepts (slug, name, domain, definition) VALUES (?, ?, ?, ?)
                        ON CONFLICT (slug) DO UPDATE SET
                          domain = CASE WHEN concepts.domain = 'UNSORTED' THEN excluded.domain ELSE concepts.domain END,
                          definition = CASE WHEN concepts.definition = '' THEN excluded.definition ELSE concepts.definition END
                        RETURNING id""")
                .params(slug(name), name, domain, definition).query(Long.class).single();
        jdbc.sql("INSERT INTO concept_mentions (concept_id, document_id, page_number) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(id, documentId, page).update();
        return id;
    }

    private Document document(UUID id) {
        return documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** "Two-Phase Commit (2PC)" → "two-phase-commit-2pc", so the same concept from different PDFs merges. */
    static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
    }
}
