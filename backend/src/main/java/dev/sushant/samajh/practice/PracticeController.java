package dev.sushant.samajh.practice;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.sushant.samajh.ai.AiService;
import dev.sushant.samajh.ai.Prompts;
import dev.sushant.samajh.documents.PageRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/** Steps 4–6 of the mastery loop: practise, revise with flashcards, track mastery. */
@RestController
@RequestMapping("/api")
class PracticeController {

    enum QType { MCQ, EXPLAIN, INTERVIEW }

    /** Shape the (cheap) model must return when writing questions for a concept. */
    record Generated(QType type, String prompt,
                     @JsonPropertyDescription("4 options for MCQ, [] otherwise") List<String> options,
                     @JsonPropertyDescription("0-3 for MCQ, -1 otherwise") int answerIndex,
                     String modelAnswer, String explanation) {}

    record GeneratedSet(List<Generated> questions) {}

    record Grade(@JsonPropertyDescription("0 to 100") int score, List<String> missing, String feedback) {}

    record SetRequest(UUID documentId, List<Long> conceptIds, boolean weakest) {}

    record QuestionView(long id, long conceptId, String conceptName, String type, String prompt, @JsonRawValue String options) {}

    record AttemptRequest(long questionId, String answer) {}

    record AttemptResult(int score, Integer answerIndex, String modelAnswer, String explanation,
                         List<String> missing, String feedback, ConceptMastery before, ConceptMastery after) {}

    record ConceptMastery(long id, String name, String domain, int score, Mastery.Stage stage, int attempts) {}

    record Flashcard(long id, long conceptId, String conceptName, String front, String back) {}

    record Review(boolean gotIt) {}

    record Schedule(double intervalDays, double ease, Instant dueAt) {}

    private final AiService ai;
    private final PageRepository pages;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    // ponytail: one global lock — fine for a single user; use a per-concept lock if generation ever runs in parallel at scale.
    private final Object generationLock = new Object();

    PracticeController(AiService ai, PageRepository pages, JdbcClient jdbc, JsonMapper json) {
        this.ai = ai;
        this.pages = pages;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---------- practice sets ----------

    /** Up to 3 questions per concept (least-practised first), max 5 concepts. Generates questions the first time. */
    @PostMapping("/practice/sets")
    List<QuestionView> newSet(@RequestBody SetRequest req) {
        List<Long> conceptIds = req.weakest() ? weakest(req.documentId(), 5) : req.conceptIds();
        if (conceptIds == null || conceptIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    req.weakest() ? "Nothing practised yet. Practise a page first." : "Choose at least one concept");
        }
        List<QuestionView> set = new ArrayList<>();
        for (long conceptId : conceptIds.stream().limit(5).toList()) {
            ensureQuestions(conceptId, req.documentId());
            set.addAll(jdbc.sql("""
                            SELECT q.id, q.concept_id, c.name AS concept_name, q.type, q.prompt, q.options::text AS options
                            FROM questions q JOIN concepts c ON c.id = q.concept_id
                            WHERE q.concept_id = ?
                            ORDER BY (SELECT count(*) FROM attempts a WHERE a.question_id = q.id), random()
                            LIMIT 3""")
                    .param(conceptId).query(QuestionView.class).list());
        }
        Collections.shuffle(set);
        return set;
    }

    @PostMapping("/practice/attempts")
    AttemptResult attempt(@RequestBody AttemptRequest req) {
        Map<String, Object> q = jdbc.sql("SELECT * FROM questions WHERE id = ?").param(req.questionId()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long conceptId = (Long) q.get("concept_id");
        String type = (String) q.get("type");
        String answer = req.answer() == null ? "" : req.answer().strip();
        if (answer.length() > 4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer is too long (max 4000 characters)");
        ConceptMastery before = mastery(conceptId);

        Grade grade;
        Integer answerIndex = null;
        if (type.equals("MCQ")) {
            answerIndex = (Integer) q.get("answer_index");
            grade = new Grade(answer.equals(String.valueOf(answerIndex)) ? 100 : 0, List.of(), "");
        } else if (answer.isEmpty()) {
            grade = new Grade(0, List.of(), "No answer given.");
        } else {
            UUID documentId = (UUID) q.get("document_id");
            int page = (Integer) q.get("page_number");
            String user = Prompts.render("grade", Map.of(
                    "title", title(documentId), "page", String.valueOf(page), "text", pages.readable(documentId, page).getText(),
                    "type", type, "question", (String) q.get("prompt"), "modelAnswer", (String) q.get("model_answer"), "answer", answer));
            grade = ai.structured("grade", Prompts.SYSTEM, user, Grade.class);
        }
        int score = Math.clamp(grade.score(), 0, 100);
        jdbc.sql("INSERT INTO attempts (question_id, concept_id, type, answer, score, feedback) VALUES (?, ?, ?, ?, ?, ?::jsonb)")
                .params(req.questionId(), conceptId, type, answer, score, json.writeValueAsString(grade)).update();
        return new AttemptResult(score, answerIndex, (String) q.get("model_answer"), (String) q.get("explanation"),
                grade.missing() == null ? List.of() : grade.missing(), grade.feedback(), before, mastery(conceptId));
    }

    // ---------- flashcards ----------

    @GetMapping("/flashcards/due")
    List<Flashcard> due(@RequestParam(required = false) UUID documentId) {
        return jdbc.sql("""
                        SELECT f.id, f.concept_id, c.name AS concept_name, f.front, f.back
                        FROM flashcards f JOIN concepts c ON c.id = f.concept_id
                        WHERE f.due_at <= now()
                          AND (CAST(? AS uuid) IS NULL OR EXISTS (SELECT 1 FROM concept_mentions m WHERE m.concept_id = f.concept_id AND m.document_id = ?))
                        ORDER BY f.due_at LIMIT 30""")
                .params(documentId, documentId).query(Flashcard.class).list();
    }

    @PostMapping("/flashcards/{id}/review")
    ConceptMastery review(@PathVariable long id, @RequestBody Review req) {
        Map<String, Object> card = jdbc.sql("SELECT concept_id, interval_days, ease FROM flashcards WHERE id = ?").param(id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Schedule next = schedule((Double) card.get("interval_days"), (Double) card.get("ease"), req.gotIt(), Instant.now());
        jdbc.sql("UPDATE flashcards SET interval_days = ?, ease = ?, due_at = ? WHERE id = ?")
                .params(next.intervalDays(), next.ease(), Timestamp.from(next.dueAt()), id).update();
        long conceptId = (Long) card.get("concept_id");
        jdbc.sql("INSERT INTO flashcard_reviews (flashcard_id, concept_id, got_it) VALUES (?, ?, ?)").params(id, conceptId, req.gotIt()).update();
        return mastery(conceptId);
    }

    /** Simple SM-2: "Got it" stretches the interval by the ease; "Again" shows the card again in 10 minutes. */
    static Schedule schedule(double intervalDays, double ease, boolean gotIt, Instant now) {
        if (!gotIt) return new Schedule(0, Math.max(1.3, ease - 0.2), now.plus(Duration.ofMinutes(10)));
        double interval = intervalDays < 1 ? 1 : intervalDays * ease;
        return new Schedule(interval, Math.min(3.0, ease + 0.05), now.plus(Duration.ofMinutes(Math.round(interval * 24 * 60))));
    }

    // ---------- mastery ----------

    /** Dashboard: every concept in the document (or all documents), weakest first among practised ones. */
    @GetMapping("/mastery")
    Map<String, Object> dashboard(@RequestParam(required = false) UUID documentId) {
        List<ConceptMastery> concepts = masteries(documentId);
        Map<String, List<ConceptMastery>> byDomain = new HashMap<>();
        concepts.forEach(c -> byDomain.computeIfAbsent(c.domain(), d -> new ArrayList<>()).add(c));
        List<Map<String, Object>> domains = byDomain.entrySet().stream()
                .map(e -> Map.<String, Object>of("domain", e.getKey(), "concepts", e.getValue().size(),
                        "score", (int) Math.round(e.getValue().stream().mapToInt(ConceptMastery::score).average().orElse(0))))
                .sorted(Comparator.comparing(m -> -((Integer) m.get("score"))))
                .toList();
        long dueCards = due(documentId).size();
        return Map.of(
                "overall", (int) Math.round(concepts.stream().mapToInt(ConceptMastery::score).average().orElse(0)),
                "mastered", concepts.stream().filter(c -> c.stage() == Mastery.Stage.MASTERED).count(),
                "concepts", concepts,
                "domains", domains,
                "weakest", concepts.stream().filter(c -> c.attempts() > 0).sorted(Comparator.comparingInt(ConceptMastery::score)).limit(5).toList(),
                "dueFlashcards", dueCards);
    }

    /** Average mastery per document, for the library cards. */
    @GetMapping("/mastery/documents")
    Map<UUID, Integer> perDocument() {
        Map<UUID, Integer> out = new HashMap<>();
        for (UUID doc : jdbc.sql("SELECT DISTINCT document_id FROM concept_mentions").query(UUID.class).list()) {
            out.put(doc, (int) Math.round(masteries(doc).stream().mapToInt(ConceptMastery::score).average().orElse(0)));
        }
        return out;
    }

    private List<Long> weakest(UUID documentId, int n) {
        return masteries(documentId).stream().filter(c -> c.attempts() > 0)
                .sorted(Comparator.comparingInt(ConceptMastery::score)).limit(n).map(ConceptMastery::id).toList();
    }

    private ConceptMastery mastery(long conceptId) {
        return masteries(null, conceptId).getFirst();
    }

    private List<ConceptMastery> masteries(UUID documentId) {
        return masteries(documentId, null);
    }

    /** Loads concepts plus their whole practice history in three queries and computes mastery for each. */
    private List<ConceptMastery> masteries(UUID documentId, Long conceptId) {
        record Row(long id, String name, String domain) {}
        List<Row> concepts = jdbc.sql("""
                        SELECT id, name, domain FROM concepts c
                        WHERE (CAST(? AS bigint) IS NULL OR c.id = ?)
                          AND EXISTS (SELECT 1 FROM concept_mentions m WHERE m.concept_id = c.id AND (CAST(? AS uuid) IS NULL OR m.document_id = ?))
                        ORDER BY name""")
                .params(conceptId, conceptId, documentId, documentId).query(Row.class).list();
        Map<Long, List<Mastery.Attempt>> attempts = new HashMap<>();
        jdbc.sql("SELECT concept_id, score, type, created_at FROM attempts").query((rs, i) -> attempts
                .computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                .add(new Mastery.Attempt(rs.getInt(2), rs.getString(3).equals("EXPLAIN"), rs.getTimestamp(4).toInstant()))).list();
        Map<Long, List<Mastery.Review>> reviews = new HashMap<>();
        jdbc.sql("SELECT concept_id, got_it, created_at FROM flashcard_reviews").query((rs, i) -> reviews
                .computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                .add(new Mastery.Review(rs.getBoolean(2), rs.getTimestamp(3).toInstant()))).list();
        Instant now = Instant.now();
        return concepts.stream().map(c -> {
            Mastery m = Mastery.of(attempts.getOrDefault(c.id(), List.of()), reviews.getOrDefault(c.id(), List.of()), now);
            return new ConceptMastery(c.id(), c.name(), c.domain(), m.score(), m.stage(), m.attempts());
        }).toList();
    }

    // ---------- generation ----------

    /**
     * First practice of a concept: write its questions (cheap model) and its flashcard.
     * Serialised so two simultaneous requests (double click, two tabs) never generate twice.
     */
    private void ensureQuestions(long conceptId, UUID preferredDocument) {
        synchronized (generationLock) {
            generateIfMissing(conceptId, preferredDocument);
        }
    }

    private void generateIfMissing(long conceptId, UUID preferredDocument) {
        Map<String, Object> concept = jdbc.sql("SELECT name, definition, deepen FROM concepts WHERE id = ?").param(conceptId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found"));
        String name = (String) concept.get("name");
        boolean exists = jdbc.sql("SELECT count(*) FROM questions WHERE concept_id = ?").param(conceptId).query(Long.class).single() > 0;
        if (!exists) {
            Map<String, Object> mention = jdbc.sql("""
                            SELECT document_id, page_number FROM concept_mentions WHERE concept_id = ?
                            ORDER BY (document_id = CAST(? AS uuid)) DESC NULLS LAST, page_number LIMIT 1""")
                    .params(conceptId, preferredDocument).query().listOfRows().stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, name + " is not linked to any page"));
            UUID documentId = (UUID) mention.get("document_id");
            int page = (Integer) mention.get("page_number");
            String deepen = concept.get("deepen") == null ? "" : "\n<notes>\n" + concept.get("deepen") + "\n</notes>\n";
            String user = Prompts.render("practice", Map.of("title", title(documentId), "page", String.valueOf(page),
                    "text", pages.readable(documentId, page).getText(), "deepen", deepen, "name", name));
            for (Generated g : ai.structured("practice", Prompts.SYSTEM, user, GeneratedSet.class, true).questions()) {
                boolean validMcq = g.options() != null && g.options().size() == 4 && g.answerIndex() >= 0 && g.answerIndex() < 4;
                if (g.type() == QType.MCQ && !validMcq) continue;   // never store a question we can't grade
                jdbc.sql("""
                                INSERT INTO questions (concept_id, document_id, page_number, type, prompt, options, answer_index, model_answer, explanation)
                                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)""")
                        .params(conceptId, documentId, page, g.type().name(), g.prompt(),
                                json.writeValueAsString(g.type() == QType.MCQ ? g.options() : List.of()),
                                g.type() == QType.MCQ ? g.answerIndex() : -1, g.modelAnswer(), g.explanation())
                        .update();
            }
        }
        String definition = (String) concept.get("definition");
        if (definition != null && !definition.isBlank()) {
            jdbc.sql("INSERT INTO flashcards (concept_id, front, back) VALUES (?, ?, ?) ON CONFLICT (concept_id) DO NOTHING")
                    .params(conceptId, name, definition).update();
        }
    }

    private String title(UUID documentId) {
        return jdbc.sql("SELECT title FROM documents WHERE id = ?").param(documentId).query(String.class).single();
    }
}
