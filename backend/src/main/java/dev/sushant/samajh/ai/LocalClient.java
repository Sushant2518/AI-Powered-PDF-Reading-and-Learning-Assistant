package dev.sushant.samajh.ai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Free local models via Ollama ({@code POST /api/chat}), e.g. qwen3.5:4b.
 * Streaming answers are newline-delimited JSON; structured answers use Ollama's {@code format: <JSON schema>}.
 * No web search, so "Beyond the PDF" isn't available. Usage is logged with cost 0.
 */
@Component
class LocalClient implements AiProvider {

    /** One NDJSON line: text piece, whether the answer is finished, why, and token counts (on the last line). */
    record Line(String text, boolean done, String doneReason, long inputTokens, long outputTokens) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    LocalClient(JsonMapper json, JdbcTemplate jdbc, @Value("${samajh.ai.local.url}") String baseUrl) {
        this.json = json;
        this.jdbc = jdbc;
        this.baseUrl = baseUrl;
    }

    /** The model chosen in Settings (read each call, so switching models needs no restart). */
    @Override
    public boolean canSearch() { return false; }

    @Override
    public String model() {
        return jdbc.queryForObject("SELECT local_model FROM settings WHERE id = 1", String.class);
    }

    @Override
    public AiService.Result stream(String system, String user, AiService.Effort effort, Consumer<String> onText, Consumer<Usage> usage) {
        String model = model();
        Map<String, Object> body = body(model, system, Map.of("role", "user", "content", user));
        body.put("stream", true);
        // Never let a local model "think": at ~15 tokens/s on a laptop, reasoning can take minutes before any text appears.
        body.put("think", false);
        HttpResponse<Stream<String>> response = send(model, body, HttpResponse.BodyHandlers.ofLines());

        StringBuilder text = new StringBuilder();
        Line last = null;
        try (Stream<String> lines = response.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                Line line = parseLine(it.next(), json);
                if (line == null) continue;
                if (!line.text().isEmpty()) {
                    text.append(line.text());
                    onText.accept(line.text());
                }
                if (line.done()) last = line;
            }
        } finally {
            usage.accept(new Usage("ollama:" + model, last == null ? 0 : last.inputTokens(),
                    last == null ? text.length() / 4 : last.outputTokens(), 0));
        }
        return new AiService.Result(text.toString(), last != null && "length".equals(last.doneReason()) ? "max_tokens" : "end_turn");
    }

    @Override
    public <T> T structured(String system, String user, Class<T> type, boolean fast, Consumer<Usage> usage) {
        String model = model();
        Map<String, Object> schema = RecordSchema.of(type);
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {   // small models sometimes break the JSON: retry once
            boolean promptOnly = noSchemaSupport.contains(model);
            // Without Ollama's schema mode, the schema goes into the system prompt instead.
            String sys = promptOnly
                    ? system + "\n\nReply with only a JSON object matching this JSON schema. No code fences, no other text:\n" + json.writeValueAsString(schema)
                    : system;
            Map<String, Object> body = body(model, sys, Map.of("role", "user", "content", user));
            body.put("think", false);   // reasoning would double the wait on a small laptop
            if (!promptOnly) body.put("format", schema);
            try {
                Line reply = complete(model, body, usage);
                return RecordSchema.requireComplete(json.readValue(stripFences(reply.text()), type));
            } catch (SchemaUnsupported e) {
                noSchemaSupport.add(model);   // e.g. qwen3.5 on current Ollama: "structured output is unavailable"
                attempt--;
            } catch (RuntimeException e) {
                if (e instanceof AiService.UserFacingException u) throw u;
                last = e;
            }
        }
        throw new AiService.UserFacingException("The local model returned malformed data twice. Try again, or try a larger model. (" + last.getMessage() + ")");
    }

    /** Models whose Ollama runner rejects {@code format} (HTTP 501); they get the schema in the prompt instead. */
    private final java.util.Set<String> noSchemaSupport = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class SchemaUnsupported extends RuntimeException {}

    /** Removes a ```json … ``` fence some models add despite instructions. */
    static String stripFences(String text) {
        String t = text.strip();
        if (!t.startsWith("```")) return t;
        t = t.substring(t.indexOf('\n') + 1);
        int end = t.lastIndexOf("```");
        return (end >= 0 ? t.substring(0, end) : t).strip();
    }

    @Override
    public SourcedText webSearch(String system, String user, Consumer<Usage> usage) {
        throw new AiService.UserFacingException("Beyond the PDF needs web search, which the local model can't do. Switch to Claude in Settings to use it.");
    }

    @Override
    public String ocr(byte[] png, String prompt, Consumer<Usage> usage) {
        String model = model();
        Map<String, Object> message = Map.of("role", "user", "content", prompt, "images", List.of(Base64.getEncoder().encodeToString(png)));
        return complete(model, body(model, null, message), usage).text().strip();
    }

    // ---------- wire format ----------

    /** One NDJSON line → its parts, or null for a blank line. */
    static Line parseLine(String line, JsonMapper json) {
        if (line.isBlank()) return null;
        JsonNode node = json.readTree(line);
        if (node.has("error")) throw new AiService.UserFacingException("Local model error: " + node.get("error").asString());
        return new Line(node.path("message").path("content").asString(""), node.path("done").asBoolean(false),
                node.path("done_reason").asString(""), node.path("prompt_eval_count").asLong(0), node.path("eval_count").asLong(0));
    }

    private Line complete(String model, Map<String, Object> body, Consumer<Usage> usage) {
        body.put("stream", false);
        Line reply = parseLine(send(model, body, HttpResponse.BodyHandlers.ofString()).body(), json);
        usage.accept(new Usage("ollama:" + model, reply.inputTokens(), reply.outputTokens(), 0));
        return reply;
    }

    private Map<String, Object> body(String model, String system, Map<String, Object> userMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", system == null ? List.of(userMessage) : List.of(Map.of("role", "system", "content", system), userMessage));
        body.put("options", Map.of("num_ctx", 8192));   // 3 pages + chat history, without exhausting an 8 GB Mac
        return body;
    }

    private <B> HttpResponse<B> send(String model, Map<String, Object> body, HttpResponse.BodyHandler<B> handler) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))   // small laptops can be slow, especially on the first call (model loading)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<B> response = http.send(request, handler);
            if (response.statusCode() == 404) {
                if (response.body() instanceof Stream<?> lines) lines.close();
                throw new AiService.UserFacingException("Local model \"" + model + "\" is not installed. Run: ollama pull " + model);
            }
            if (response.statusCode() >= 400) {
                String error = response.body() instanceof String b ? b : "";
                if (response.body() instanceof Stream<?> lines) lines.close();
                if (response.statusCode() == 501 && error.contains("structured output")) throw new SchemaUnsupported();
                throw new AiService.UserFacingException("Local model error (HTTP " + response.statusCode() + ")" + (error.isBlank() ? "." : ": " + error));
            }
            return response;
        } catch (ConnectException e) {
            throw new AiService.UserFacingException("Ollama is not running. Start it with: ollama serve");
        } catch (HttpTimeoutException e) {
            throw new AiService.UserFacingException("The local model is too slow on this machine. Try a smaller model (e.g. qwen3.5:2b) in Settings.");
        } catch (IOException e) {
            throw new AiService.UserFacingException("Could not reach the local model: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiService.UserFacingException("Interrupted.");
        }
    }
}
