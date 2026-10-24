package dev.sushant.samajh.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Gemini API (free tier) over plain REST: {@code generateContent} / {@code streamGenerateContent?alt=sse}.
 * Free-tier Google Search grounding needs {@code gemini-2.5-flash} (or -lite); usage is logged with cost 0.
 */
@Component
class GeminiClient implements AiProvider {

    /** Text, finish reason and token counts from one response (or one streamed chunk). */
    record Chunk(String text, String finishReason, long inputTokens, long outputTokens) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String searchModel;
    private final List<String> trustedDomains;

    GeminiClient(JsonMapper json,
                 @Value("${GEMINI_API_KEY:}") String apiKey,
                 @Value("${samajh.ai.gemini.base-url}") String baseUrl,
                 @Value("${samajh.ai.gemini.model}") String model,
                 @Value("${samajh.ai.gemini.search-model}") String searchModel,
                 @Value("${samajh.ai.trusted-domains}") List<String> trustedDomains) {
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.searchModel = searchModel;
        this.trustedDomains = trustedDomains;
    }

    @Override
    public String model() { return model; }

    @Override
    public AiService.Result stream(String system, String user, AiService.Effort effort, Consumer<String> onText, Consumer<Usage> usage) {
        Map<String, Object> body = body(system, List.of(Map.of("text", user)));
        if (effort == AiService.Effort.LOW) body.put("generationConfig", Map.of("thinkingConfig", Map.of("thinkingBudget", 0)));
        HttpResponse<Stream<String>> response = send(model, "streamGenerateContent?alt=sse", body, HttpResponse.BodyHandlers.ofLines());

        StringBuilder text = new StringBuilder();
        Chunk last = null;
        try (Stream<String> lines = response.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                Chunk chunk = parseSseLine(it.next(), json);
                if (chunk == null) continue;
                if (!chunk.text().isEmpty()) {
                    text.append(chunk.text());
                    onText.accept(chunk.text());
                }
                last = chunk;
            }
        } finally {
            long in = last == null ? 0 : last.inputTokens();
            long out = last == null || last.outputTokens() == 0 ? text.length() / 4 : last.outputTokens();
            usage.accept(new Usage(model, in, out, 0));
        }
        return new AiService.Result(text.toString(), stopReason(last == null ? null : last.finishReason()));
    }

    @Override
    public <T> T structured(String system, String user, Class<T> type, boolean fast, Consumer<Usage> usage) {
        Map<String, Object> body = body(system, List.of(Map.of("text", user)));
        body.put("generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", RecordSchema.of(type)));
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {   // retry once: models occasionally break the JSON
            Chunk reply = complete(model, body, usage).chunk();
            try {
                return json.readValue(reply.text(), type);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw new AiService.UserFacingException("Gemini returned malformed data twice. Try again. (" + last.getMessage() + ")");
    }

    @Override
    public SourcedText webSearch(String system, String user, Consumer<Usage> usage) {
        Map<String, Object> body = body(system, List.of(Map.of("text", user)));
        body.put("tools", List.of(Map.of("google_search", Map.of())));
        Reply reply = complete(searchModel, body, usage);
        JsonNode grounding = reply.candidate().path("groundingMetadata");
        List<SourcedText.Chunk> chunks = new ArrayList<>();
        for (JsonNode c : grounding.path("groundingChunks")) {
            chunks.add(new SourcedText.Chunk(c.path("web").path("uri").asString(""), c.path("web").path("title").asString("")));
        }
        List<SourcedText.Support> supports = new ArrayList<>();
        for (JsonNode s : grounding.path("groundingSupports")) {
            List<Integer> indices = new ArrayList<>();
            s.path("groundingChunkIndices").forEach(i -> indices.add(i.asInt()));
            supports.add(new SourcedText.Support(s.path("segment").path("text").asString(""), indices));
        }
        return SourcedText.fromGrounding(reply.chunk().text(), chunks, supports, trustedDomains);
    }

    @Override
    public String ocr(byte[] png, String prompt, Consumer<Usage> usage) {
        Map<String, Object> image = Map.of("inline_data", Map.of("mime_type", "image/png", "data", Base64.getEncoder().encodeToString(png)));
        Map<String, Object> body = body(null, List.of(image, Map.of("text", prompt)));
        return complete(model, body, usage).chunk().text().strip();
    }

    // ---------- wire format ----------

    private record Reply(Chunk chunk, JsonNode candidate) {}

    /** One SSE line → its chunk, or null for blank/comment lines. Thought parts (model reasoning) are skipped. */
    static Chunk parseSseLine(String line, JsonMapper json) {
        if (!line.startsWith("data:")) return null;
        JsonNode node = json.readTree(line.substring(5).strip());
        if (node.has("error")) throw error(node.path("error").path("code").asInt(500), node.path("error").path("message").asString(""));
        return chunk(node);
    }

    private static Chunk chunk(JsonNode node) {
        String blocked = node.path("promptFeedback").path("blockReason").asString("");
        if (!blocked.isEmpty()) throw declined();
        JsonNode candidate = node.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asString(""));
        }
        String finish = candidate.path("finishReason").asString("");
        if (List.of("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII").contains(finish)) throw declined();
        JsonNode u = node.path("usageMetadata");
        return new Chunk(text.toString(), finish, u.path("promptTokenCount").asLong(0),
                u.path("candidatesTokenCount").asLong(0) + u.path("thoughtsTokenCount").asLong(0));
    }

    private Reply complete(String m, Map<String, Object> body, Consumer<Usage> usage) {
        HttpResponse<String> response = send(m, "generateContent", body, HttpResponse.BodyHandlers.ofString());
        JsonNode node = json.readTree(response.body());
        Chunk chunk = chunk(node);
        usage.accept(new Usage(m, chunk.inputTokens(), chunk.outputTokens(), 0));
        return new Reply(chunk, node.path("candidates").path(0));
    }

    private Map<String, Object> body(String system, List<Map<String, Object>> parts) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (system != null) body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        return body;
    }

    private <B> HttpResponse<B> send(String m, String method, Map<String, Object> body, HttpResponse.BodyHandler<B> handler) {
        if (apiKey.isBlank()) {
            throw new AiService.UserFacingException("Missing Gemini API key. Get a free one at aistudio.google.com/apikey, put GEMINI_API_KEY=... in backend/.env and restart the backend.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + m + ":" + method))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<B> response = http.send(request, handler);
            if (response.statusCode() >= 400) {
                String message = "";
                if (response.body() instanceof String s) message = s;
                else if (response.body() instanceof Stream<?> lines) {
                    try (Stream<?> l = lines) { message = String.join("\n", l.map(Object::toString).toList()); }
                }
                throw error(response.statusCode(), message);
            }
            return response;
        } catch (IOException e) {
            throw new AiService.UserFacingException("Could not reach Gemini: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiService.UserFacingException("Interrupted.");
        }
    }

    private static AiService.UserFacingException error(int status, String message) {
        String m = message == null ? "" : message;
        if (status == 429) return new AiService.UserFacingException("Gemini free-tier limit reached. Wait a minute and try again (daily limits reset at midnight Pacific time).");
        if (status == 401 || status == 403 || m.contains("API key")) {
            return new AiService.UserFacingException("Invalid Gemini API key. Check GEMINI_API_KEY in backend/.env and restart the backend.");
        }
        if (status == 503 || status == 500) return new AiService.UserFacingException("Gemini is busy. Try again in a moment.");
        return new AiService.UserFacingException("Gemini API error (" + status + "). Try again.");
    }

    private static AiService.UserFacingException declined() {
        return new AiService.UserFacingException("Gemini declined this request. Try a different part of the page.");
    }

    /** Gemini finish reasons → the stop reasons the controllers check before caching. */
    private static String stopReason(String finishReason) {
        if ("MAX_TOKENS".equals(finishReason)) return "max_tokens";
        return "STOP".equals(finishReason) ? "end_turn" : finishReason;
    }
}
