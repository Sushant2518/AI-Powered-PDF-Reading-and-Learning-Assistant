package dev.sushant.samajh.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LocalClientTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsStreamingPiecesAndTheFinalLine() {
        var piece = LocalClient.parseLine("{\"model\":\"qwen3.5:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"Hel\"},\"done\":false}", JSON);
        assertEquals("Hel", piece.text());
        assertEquals(false, piece.done());

        var last = LocalClient.parseLine("{\"message\":{\"content\":\"\"},\"done\":true,\"done_reason\":\"length\",\"prompt_eval_count\":900,\"eval_count\":120}", JSON);
        assertTrue(last.done());
        assertEquals("length", last.doneReason());
        assertEquals(900, last.inputTokens());
        assertEquals(120, last.outputTokens());
    }

    @Test
    void stripsCodeFencesAroundJson() {
        assertEquals("{\"a\":1}", LocalClient.stripFences("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", LocalClient.stripFences("  {\"a\":1}  "));
    }

    @Test
    void blankLinesAreSkippedAndErrorsAreReadable() {
        assertNull(LocalClient.parseLine("  ", JSON));
        var e = assertThrows(AiService.UserFacingException.class, () -> LocalClient.parseLine("{\"error\":\"model requires more system memory\"}", JSON));
        assertTrue(e.getMessage().contains("more system memory"));
    }
}
