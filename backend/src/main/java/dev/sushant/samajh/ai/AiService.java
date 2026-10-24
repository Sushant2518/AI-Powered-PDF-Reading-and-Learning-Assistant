package dev.sushant.samajh.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.WebSearchTool20250305;
import com.anthropic.models.messages.WebSearchTool20260209;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The only place that talks to Claude. Streams text, logs token usage, and enforces the monthly budget. */
@Service
public class AiService {

    /** An error whose message is safe and useful to show in the UI. */
    public static class UserFacingException extends RuntimeException {
        public UserFacingException(String message) { super(message); }
    }

    public record Result(String text, String stopReason) {}

    /** The single row of the settings table. */
    public record Settings(double monthlyBudgetUsd) {}

    private static final String OCR_PROMPT = """
            Transcribe all the text on this book page exactly as printed, keeping paragraphs and line breaks.
            Keep Devanagari (Marathi/Hindi) in Devanagari Unicode. Keep code exactly. Output only the page text, nothing else.""";

    private final JdbcTemplate jdbc;
    private final String apiKey;
    private final String model;
    private final double inputPrice;
    private final double outputPrice;
    private final double webSearchPrice;
    private final String fastModel;
    private final double fastInputPrice;
    private final double fastOutputPrice;
    private final List<String> trustedDomains;
    private volatile AnthropicClient client;

    AiService(JdbcTemplate jdbc,
              @Value("${ANTHROPIC_API_KEY:}") String apiKey,
              @Value("${samajh.ai.model}") String model,
              @Value("${samajh.ai.input-price}") double inputPrice,
              @Value("${samajh.ai.output-price}") double outputPrice,
              @Value("${samajh.ai.web-search-price}") double webSearchPrice,
              @Value("${samajh.ai.trusted-domains}") List<String> trustedDomains,
              @Value("${samajh.ai.fast-model}") String fastModel,
              @Value("${samajh.ai.fast-input-price}") double fastInputPrice,
              @Value("${samajh.ai.fast-output-price}") double fastOutputPrice) {
        this.jdbc = jdbc;
        this.apiKey = apiKey;
        this.model = model;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.webSearchPrice = webSearchPrice;
        this.trustedDomains = trustedDomains;
        this.fastModel = fastModel;
        this.fastInputPrice = fastInputPrice;
        this.fastOutputPrice = fastOutputPrice;
    }

    public Settings settings() {
        return jdbc.queryForObject("SELECT monthly_budget_usd FROM settings WHERE id = 1", (rs, i) -> new Settings(rs.getDouble(1)));
    }

    /** The model answering (stored next to cached results). */
    public String model() { return model; }

    public double spentThisMonth() {
        BigDecimal spent = jdbc.queryForObject(
                "SELECT coalesce(sum(cost_usd), 0) FROM ai_usage WHERE created_at >= date_trunc('month', now())",
                BigDecimal.class);
        return spent == null ? 0 : spent.doubleValue();
    }

    public double monthlyBudget() { return settings().monthlyBudgetUsd(); }

    /** Streams Claude's text to {@code onText} and returns the full text. {@code onText} may throw to abort. */
    public Result stream(String feature, String system, String user, OutputConfig.Effort effort, Consumer<String> onText) {
        checkBudget();
        MessageCreateParams params = withFallback(MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(effort).build())
                .addUserMessage(user), model)
                .build();

        StringBuilder text = new StringBuilder();
        long inputTokens = 0, outputTokens = 0;
        String stopReason = null;
        try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params)) {
            Iterator<RawMessageStreamEvent> events = stream.stream().iterator();
            while (events.hasNext()) {
                RawMessageStreamEvent event = events.next();
                if (event.messageStart().isPresent()) {
                    var usage = event.messageStart().get().message().usage();
                    inputTokens = usage.inputTokens()
                            + usage.cacheCreationInputTokens().orElse(0L)
                            + usage.cacheReadInputTokens().orElse(0L);
                }
                var delta = event.contentBlockDelta().flatMap(d -> d.delta().text());
                if (delta.isPresent()) {
                    text.append(delta.get().text());
                    onText.accept(delta.get().text());
                }
                if (event.messageDelta().isPresent()) {
                    var md = event.messageDelta().get();
                    outputTokens = md.usage().outputTokens();
                    if (md.delta().stopReason().isPresent()) stopReason = md.delta().stopReason().get().asString();
                }
            }
        } catch (AnthropicServiceException e) {
            throw userFacing(e);
        } finally {
            // A cancelled stream never reports output tokens; estimate (~4 chars/token) so the budget isn't undercounted.
            if (outputTokens == 0) outputTokens = text.length() / 4;
            if (inputTokens + outputTokens > 0) recordUsage(feature, false, inputTokens, outputTokens, 0);
        }

        if ("refusal".equals(stopReason)) throw refused();
        return new Result(text.toString(), stopReason);
    }

    /** One non-streaming call whose answer is parsed into {@code type} (JSON schema derived from the record). */
    public <T> T structured(String feature, String system, String user, Class<T> type) {
        return structured(feature, system, user, type, false);
    }

    /** As above; {@code fast} uses the cheap model (bulk generation such as practice questions). */
    public <T> T structured(String feature, String system, String user, Class<T> type, boolean fast) {
        checkBudget();
        String chosen = fast ? fastModel : model;
        StructuredMessageCreateParams<T> params = withFallback(MessageCreateParams.builder()
                .model(chosen)
                .maxTokens(16000L)
                .system(system)
                .addUserMessage(user), chosen)
                .outputConfig(type)
                .build();
        StructuredMessage<T> response;
        try {
            response = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            throw userFacing(e);
        }
        var usage = response.usage();
        recordUsage(feature, fast,
                usage.inputTokens() + usage.cacheCreationInputTokens().orElse(0L) + usage.cacheReadInputTokens().orElse(0L),
                usage.outputTokens(), 0);
        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) throw refused();
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow(() -> new UserFacingException("Claude returned no answer. Try again."))
                .text();
    }

    /**
     * Answers using Claude's server-side web search, limited to the trusted domains.
     * Resumes up to 3 times when the server pauses a long search turn ({@code pause_turn}).
     */
    public SourcedText webSearch(String feature, String system, String user) {
        checkBudget();
        MessageCreateParams.Builder params = withFallback(MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(user), model);
        // The dynamic-filtering search tool needs Opus/Sonnet; Haiku uses the basic one.
        if (isHaiku(model)) params.addTool(WebSearchTool20250305.builder().maxUses(5L).allowedDomains(trustedDomains).build());
        else params.addTool(WebSearchTool20260209.builder().maxUses(5L).allowedDomains(trustedDomains).build());
        List<ContentBlock> content = new ArrayList<>();
        for (int turn = 0; turn < 4; turn++) {
            Message response;
            try {
                response = client().messages().create(params.build());
            } catch (AnthropicServiceException e) {
                throw userFacing(e);
            }
            var usage = response.usage();
            long searches = usage.serverToolUse().map(u -> u.webSearchRequests()).orElse(0L);
            recordUsage(feature, false,
                    usage.inputTokens() + usage.cacheCreationInputTokens().orElse(0L) + usage.cacheReadInputTokens().orElse(0L),
                    usage.outputTokens(), searches * webSearchPrice);
            content.addAll(response.content());
            String stop = response.stopReason().map(r -> r.asString()).orElse("");
            if (stop.equals("refusal")) throw refused();
            if (!stop.equals("pause_turn")) break;
            params.addMessage(response);   // resume: the server continues from the trailing search
        }
        return SourcedText.from(content);
    }

    /** Reads a page image (PNG) and returns its text, using the cheap model. */
    public String ocr(byte[] png) {
        String b64 = Base64.getEncoder().encodeToString(png);
        checkBudget();
        MessageCreateParams params = withFallback(MessageCreateParams.builder()
                .model(fastModel)
                .maxTokens(8000L)
                .addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder().data(b64).mediaType(Base64ImageSource.MediaType.IMAGE_PNG).build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(OCR_PROMPT).build()))), fastModel)
                .build();
        Message response;
        try {
            response = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            throw userFacing(e);
        }
        recordUsage("ocr", true, response.usage().inputTokens(), response.usage().outputTokens(), 0);
        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) throw refused();
        return response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).reduce("", String::concat).strip();
    }

    /** Retry server-side on another model if a safety classifier wrongly declines (e.g. security pages). Not available for Haiku. */
    private static MessageCreateParams.Builder withFallback(MessageCreateParams.Builder builder, String model) {
        if (isHaiku(model)) return builder;
        return builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }

    private static boolean isHaiku(String model) { return model.contains("haiku"); }

    private void checkBudget() {
        double budget = monthlyBudget();
        if (spentThisMonth() >= budget) {
            throw new UserFacingException("Monthly AI budget of $%.0f reached. Raise it in Settings.".formatted(budget));
        }
    }

    private static UserFacingException userFacing(AnthropicServiceException e) {
        return new UserFacingException(switch (e.statusCode()) {
            case 401 -> "Missing or invalid Claude API key. Put ANTHROPIC_API_KEY=... in backend/.env and restart the backend.";
            case 429, 529 -> "Claude is busy or rate-limited. Try again in a minute.";
            default -> "Claude API error (" + e.statusCode() + "). Try again.";
        });
    }

    private static UserFacingException refused() {
        return new UserFacingException("Claude declined this request. Try a different part of the page.");
    }

    private void recordUsage(String feature, boolean fast, long in, long out, double extraCost) {
        double cost = fast
                ? (in * fastInputPrice + out * fastOutputPrice) / 1_000_000
                : (in * inputPrice + out * outputPrice) / 1_000_000 + extraCost;
        jdbc.update("INSERT INTO ai_usage (feature, model, input_tokens, output_tokens, cost_usd) VALUES (?, ?, ?, ?, ?)",
                feature, fast ? fastModel : model, in, out, cost);
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    // An explicit key from .env wins; otherwise the SDK's own lookup (env var or `ant auth login` profile).
                    var builder = AnthropicOkHttpClient.builder().fromEnv();
                    client = apiKey.isBlank() ? builder.build() : builder.apiKey(apiKey).build();
                }
            }
        }
        return client;
    }
}
