package dev.sushant.samajh.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AskControllerTest {

    @Test
    void parsesAnswerBeyondQueryAndFollowUps() {
        var a = AskController.Answer.parse("""
                Retries are safe for idempotent calls [p.3].
                The book doesn't cover the IETF header.
                <<<BEYOND>>>
                Idempotency-Key HTTP header standard
                <<<FOLLOWUPS>>>
                1. Is PUT idempotent?
                - How long to keep keys?
                * What about DELETE?
                A fourth one
                """);
        assertEquals("Retries are safe for idempotent calls [p.3].\nThe book doesn't cover the IETF header.", a.text());
        assertEquals("Idempotency-Key HTTP header standard", a.beyondQuery());
        assertEquals(List.of("Is PUT idempotent?", "How long to keep keys?", "What about DELETE?"), a.followUps());
    }

    @Test
    void answerFullyFromPdfHasNoBeyond() {
        var a = AskController.Answer.parse("All in the book [p.2].\n<<<FOLLOWUPS>>>\nNext?");
        assertEquals("", a.beyondQuery());
        assertEquals(List.of("Next?"), a.followUps());
        assertEquals("All in the book [p.2].", AskController.Answer.parse("All in the book [p.2].").text());
    }
}
