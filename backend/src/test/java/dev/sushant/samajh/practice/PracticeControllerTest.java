package dev.sushant.samajh.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PracticeControllerTest {

    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void gotItStretchesTheInterval() {
        var first = PracticeController.schedule(0, 2.5, true, NOW);
        assertEquals(1, first.intervalDays());
        assertEquals(NOW.plus(Duration.ofDays(1)), first.dueAt());
        var second = PracticeController.schedule(first.intervalDays(), first.ease(), true, NOW);
        assertEquals(2.55, second.intervalDays(), 1e-9);
        assertEquals(2.6, second.ease(), 1e-9);
    }

    @Test
    void againResetsAndShowsSoon() {
        var again = PracticeController.schedule(10, 1.4, false, NOW);
        assertEquals(0, again.intervalDays());
        assertEquals(1.3, again.ease(), 1e-9);   // never below 1.3
        assertEquals(NOW.plus(Duration.ofMinutes(10)), again.dueAt());
    }
}
