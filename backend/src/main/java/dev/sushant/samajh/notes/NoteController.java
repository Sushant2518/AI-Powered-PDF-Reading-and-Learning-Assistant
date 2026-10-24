package dev.sushant.samajh.notes;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Notes and highlights, both anchored to a page and (optionally) the selected text's position on it. */
@RestController
@RequestMapping("/api")
class NoteController {

    record Note(long id, int pageNumber, String selectedText, @JsonRawValue String anchor,
                String content, String source, OffsetDateTime createdAt) {}

    record Highlight(long id, int pageNumber, String selectedText, @JsonRawValue String anchor, String color) {}

    /** anchor = {"rects":[{"x","y","w","h"}]}, each value 0..1 relative to the page. */
    record NewNote(int page, String selectedText, Map<String, Object> anchor, String content, String source) {}

    record NewHighlight(int page, String selectedText, Map<String, Object> anchor, String color) {}

    record NoteUpdate(String content) {}

    private static final Set<String> COLORS = Set.of("yellow", "green", "blue", "pink");
    private static final String NOTE_COLUMNS = "id, page_number, selected_text, anchor::text AS anchor, content, source, created_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    NoteController(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/documents/{id}/notes")
    List<Note> notes(@PathVariable UUID id) {
        return jdbc.sql("SELECT " + NOTE_COLUMNS + " FROM notes WHERE document_id = ? ORDER BY page_number, created_at")
                .param(id).query(Note.class).list();
    }

    @PostMapping("/documents/{id}/notes")
    Note addNote(@PathVariable UUID id, @RequestBody NewNote req) {
        String source = "AI".equals(req.source()) ? "AI" : "ME";
        long noteId = jdbc.sql("""
                        INSERT INTO notes (document_id, page_number, selected_text, anchor, content, source)
                        VALUES (?, ?, ?, ?::jsonb, ?, ?) RETURNING id""")
                .params(id, req.page(), req.selectedText(), anchor(req.anchor()), req.content() == null ? "" : req.content(), source)
                .query(Long.class).single();
        return note(noteId);
    }

    @PutMapping("/notes/{noteId}")
    Note updateNote(@PathVariable long noteId, @RequestBody NoteUpdate req) {
        int updated = jdbc.sql("UPDATE notes SET content = ?, updated_at = now() WHERE id = ?")
                .params(req.content() == null ? "" : req.content(), noteId).update();
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return note(noteId);
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNote(@PathVariable long noteId) {
        jdbc.sql("DELETE FROM notes WHERE id = ?").param(noteId).update();
    }

    @GetMapping("/documents/{id}/highlights")
    List<Highlight> highlights(@PathVariable UUID id) {
        return jdbc.sql("SELECT id, page_number, selected_text, anchor::text AS anchor, color FROM highlights WHERE document_id = ? ORDER BY page_number, id")
                .param(id).query(Highlight.class).list();
    }

    @PostMapping("/documents/{id}/highlights")
    Highlight addHighlight(@PathVariable UUID id, @RequestBody NewHighlight req) {
        if (!COLORS.contains(req.color()) || req.anchor() == null || req.selectedText() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Need selected text, anchor and a color: " + COLORS);
        }
        return jdbc.sql("""
                        INSERT INTO highlights (document_id, page_number, selected_text, anchor, color) VALUES (?, ?, ?, ?::jsonb, ?)
                        RETURNING id, page_number, selected_text, anchor::text AS anchor, color""")
                .params(id, req.page(), req.selectedText(), anchor(req.anchor()), req.color())
                .query(Highlight.class).single();
    }

    @DeleteMapping("/highlights/{highlightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteHighlight(@PathVariable long highlightId) {
        jdbc.sql("DELETE FROM highlights WHERE id = ?").param(highlightId).update();
    }

    private Note note(long id) {
        return jdbc.sql("SELECT " + NOTE_COLUMNS + " FROM notes WHERE id = ?").param(id).query(Note.class).single();
    }

    private String anchor(Map<String, Object> anchor) {
        return anchor == null ? null : json.writeValueAsString(anchor);
    }
}
