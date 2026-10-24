package dev.sushant.samajh.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsTextAndUsageAndSkipsThoughts() {
        var chunk = GeminiClient.parseSseLine("""
                data: {"candidates":[{"content":{"parts":[{"text":"thinking…","thought":true},{"text":"Hello"}]},"finishReason":"STOP"}],
                "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":3,"thoughtsTokenCount":2}}""".replace("\n", ""), JSON);
        assertEquals("Hello", chunk.text());
        assertEquals("STOP", chunk.finishReason());
        assertEquals(12, chunk.inputTokens());
        assertEquals(5, chunk.outputTokens());
    }

    @Test
    void ignoresNonDataLinesAndRejectsBlockedContent() {
        assertNull(GeminiClient.parseSseLine("", JSON));
        assertNull(GeminiClient.parseSseLine(": keep-alive", JSON));
        assertThrows(AiService.UserFacingException.class,
                () -> GeminiClient.parseSseLine("data: {\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", JSON));
        var e = assertThrows(AiService.UserFacingException.class,
                () -> GeminiClient.parseSseLine("data: {\"error\":{\"code\":429,\"message\":\"quota\"}}", JSON));
        assertEquals(true, e.getMessage().contains("free-tier limit"));
    }
}
