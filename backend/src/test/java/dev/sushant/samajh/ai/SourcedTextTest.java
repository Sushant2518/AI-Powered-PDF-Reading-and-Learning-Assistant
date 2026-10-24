package dev.sushant.samajh.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sushant.samajh.ai.SourcedText.Segment;
import dev.sushant.samajh.ai.SourcedText.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourcedTextTest {

    static Source src(String url) { return new Source(0, url, "Title of " + url); }

    @Test
    void keepsCitedTextDropsUnsourcedClaims() {
        SourcedText r = SourcedText.of(List.of(
                new Segment("I'll search for that. Here is what I found:\n\n## What's current\n- ", List.of()),
                new Segment("An IETF draft standardises the Idempotency-Key header.", List.of(src("https://datatracker.ietf.org/a"))),
                new Segment("\n- Everyone agrees this is the best approach.\n- ", List.of()),
                new Segment("Stripe accepts idempotency keys on POST.", List.of(src("https://docs.stripe.com/b"), src("https://datatracker.ietf.org/a"))),
                new Segment("\n\n## Alternatives\nSome unsourced opinion.", List.of())));

        assertEquals("""
                ## What's current
                - An IETF draft standardises the Idempotency-Key header. [1]
                - Stripe accepts idempotency keys on POST. [2] [1]""", r.markdown());
        assertEquals(List.of(1, 2), r.sources().stream().map(Source::n).toList());
        assertEquals("https://docs.stripe.com/b", r.sources().get(1).url());
        assertEquals(3, r.hidden());   // intro sentence, "Everyone agrees…", "Some unsourced opinion."
        assertFalse(r.markdown().contains("Alternatives"));   // heading with nothing sourced under it is removed
    }

    @Test
    void geminiGroundingKeepsOnlyTrustedSources() {
        String answer = "## What's current\n* An IETF draft standardises the header. * A blog says it is magic.\n\n## In the real world\n* Stripe accepts idempotency keys.";
        var chunks = List.of(
                new SourcedText.Chunk("https://vertexaisearch.cloud.google.com/redirect/1", "datatracker.ietf.org"),
                new SourcedText.Chunk("https://vertexaisearch.cloud.google.com/redirect/2", "random-blog.com"),
                new SourcedText.Chunk("https://vertexaisearch.cloud.google.com/redirect/3", "docs.stripe.com"));
        var supports = List.of(
                new SourcedText.Support("An IETF draft standardises the header.", List.of(0, 1)),
                new SourcedText.Support("A blog says it is magic.", List.of(1)),        // untrusted only → hidden
                new SourcedText.Support("Stripe accepts idempotency keys.", List.of(2)));
        SourcedText r = SourcedText.fromGrounding(answer, chunks, supports, List.of("ietf.org", "stripe.com"));

        assertEquals("""
                ## What's current
                * An IETF draft standardises the header. [1]
                ## In the real world
                * Stripe accepts idempotency keys. [2]""", r.markdown().replace("\n\n", "\n"));
        assertEquals(List.of("datatracker.ietf.org", "docs.stripe.com"), r.sources().stream().map(Source::title).toList());
        assertEquals(1, r.hidden());
    }

    @Test
    void nothingSourcedMeansEmpty() {
        SourcedText r = SourcedText.of(List.of(new Segment("I couldn't find anything reliable.", List.of())));
        assertTrue(r.isEmpty());
        assertEquals("", r.markdown());
    }
}
