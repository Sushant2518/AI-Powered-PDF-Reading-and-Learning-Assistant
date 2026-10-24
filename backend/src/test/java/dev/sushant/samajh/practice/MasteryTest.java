package dev.sushant.samajh.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sushant.samajh.practice.Mastery.Attempt;
import dev.sushant.samajh.practice.Mastery.Review;
import dev.sushant.samajh.practice.Mastery.Stage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasteryTest {

    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    static Instant daysAgo(double d) { return NOW.minus(Duration.ofMinutes((long) (d * 24 * 60))); }

    @Test
    void untouchedConceptIsRead() {
        assertEquals(new Mastery(0, Stage.READ, 0), Mastery.of(List.of(), List.of(), NOW));
    }

    @Test
    void newerAttemptsWeighMore() {
        // weights 1,2,3 → (0*1 + 50*2 + 100*3) / 6 = 66.7, practised today → no decay
        Mastery m = Mastery.of(List.of(new Attempt(0, false, daysAgo(0.3)), new Attempt(50, false, daysAgo(0.2)), new Attempt(100, false, daysAgo(0.1))), List.of(), NOW);
        assertEquals(67, m.score());
        assertEquals(Stage.PRACTISED, m.stage());
    }

    @Test
    void masteredNeedsHighScoreAndAGoodExplainBack() {
        List<Attempt> mcqOnly = List.of(new Attempt(100, false, daysAgo(0)), new Attempt(100, false, daysAgo(0)), new Attempt(100, false, daysAgo(0)));
        assertEquals(Stage.PRACTISED, Mastery.of(mcqOnly, List.of(), NOW).stage());

        List<Attempt> withExplain = List.of(new Attempt(90, false, daysAgo(0)), new Attempt(80, true, daysAgo(0)), new Attempt(95, false, daysAgo(0)));
        assertEquals(Stage.MASTERED, Mastery.of(withExplain, List.of(), NOW).stage());
    }

    @Test
    void flashcardsCountThirtyPercent() {
        // practice 100, flash 50% → 0.7*100 + 0.3*50 = 85
        Mastery m = Mastery.of(List.of(new Attempt(100, true, daysAgo(0))),
                List.of(new Review(true, daysAgo(0)), new Review(false, daysAgo(0))), NOW);
        assertEquals(85, m.score());
    }

    @Test
    void knowledgeFadesWithoutRevisionButNeverBelowSixtyPercent() {
        Attempt perfect = new Attempt(100, true, daysAgo(45));
        assertEquals(75, Mastery.of(List.of(perfect), List.of(), NOW).score());   // 100 * (0.6 + 0.4/e) = 74.7
        int ancient = Mastery.of(List.of(new Attempt(100, true, daysAgo(1000))), List.of(), NOW).score();
        assertTrue(ancient >= 60 && ancient < 61);
    }
}
