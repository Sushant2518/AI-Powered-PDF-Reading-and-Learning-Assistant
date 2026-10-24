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
    void nothingSourcedMeansEmpty() {
        SourcedText r = SourcedText.of(List.of(new Segment("I couldn't find anything reliable.", List.of())));
        assertTrue(r.isEmpty());
        assertEquals("", r.markdown());
    }
}
