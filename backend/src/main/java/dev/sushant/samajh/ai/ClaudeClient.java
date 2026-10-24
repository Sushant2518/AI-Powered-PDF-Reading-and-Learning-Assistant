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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Claude (paid API) via the official anthropic-java SDK. */
@Component
class ClaudeClient implements AiProvider {

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

    ClaudeClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                 @Value("${samajh.ai.model}") String model,
                 @Value("${samajh.ai.input-price}") double inputPrice,
                 @Value("${samajh.ai.output-price}") double outputPrice,
                 @Value("${samajh.ai.web-search-price}") double webSearchPrice,
                 @Value("${samajh.ai.trusted-domains}") List<String> trustedDomains,
                 @Value("${samajh.ai.fast-model}") String fastModel,
                 @Value("${samajh.ai.fast-input-price}") double fastInputPrice,
                 @Value("${samajh.ai.fast-output-price}") double fastOutputPrice) {
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

    @Override
    public String model() { return model; }

    @Override
    public AiService.Result stream(String system, String user, AiService.Effort effort, Consumer<String> onText, Consumer<Usage> usage) {
        MessageCreateParams params = withFallback(MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(effort == AiService.Effort.LOW ? OutputConfig.Effort.LOW : OutputConfig.Effort.MEDIUM).build())
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
                    var u = event.messageStart().get().message().usage();
                    inputTokens = u.inputTokens() + u.cacheCreationInputTokens().orElse(0L) + u.cacheReadInputTokens().orElse(0L);
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
            if (inputTokens + outputTokens > 0) usage.accept(cost(false, inputTokens, outputTokens, 0));
        }
        if ("refusal".equals(stopReason)) throw refused();
        return new AiService.Result(text.toString(), stopReason);
    }

    @Override
    public <T> T structured(String system, String user, Class<T> type, boolean fast, Consumer<Usage> usage) {
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
        var u = response.usage();
        usage.accept(cost(fast, u.inputTokens() + u.cacheCreationInputTokens().orElse(0L) + u.cacheReadInputTokens().orElse(0L), u.outputTokens(), 0));
        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) throw refused();
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow(() -> new AiService.UserFacingException("Claude returned no answer. Try again."))
                .text();
    }

    /** Server-side web search limited to the trusted domains; resumes up to 3 times on {@code pause_turn}. */
    @Override
    public SourcedText webSearch(String system, String user, Consumer<Usage> usage) {
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
            var u = response.usage();
            long searches = u.serverToolUse().map(s -> s.webSearchRequests()).orElse(0L);
            usage.accept(cost(false, u.inputTokens() + u.cacheCreationInputTokens().orElse(0L) + u.cacheReadInputTokens().orElse(0L),
                    u.outputTokens(), searches * webSearchPrice));
            content.addAll(response.content());
            String stop = response.stopReason().map(r -> r.asString()).orElse("");
            if (stop.equals("refusal")) throw refused();
            if (!stop.equals("pause_turn")) break;
            params.addMessage(response);   // resume: the server continues from the trailing search
        }
        return SourcedText.fromClaude(content);
    }

    @Override
    public String ocr(byte[] png, String prompt, Consumer<Usage> usage) {
        MessageCreateParams params = withFallback(MessageCreateParams.builder()
                .model(fastModel)
                .maxTokens(8000L)
                .addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(png))
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_PNG).build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build()))), fastModel)
                .build();
        Message response;
        try {
            response = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            throw userFacing(e);
        }
        usage.accept(cost(true, response.usage().inputTokens(), response.usage().outputTokens(), 0));
        if (response.stopReason().map(r -> r.asString().equals("refusal")).orElse(false)) throw refused();
        return response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).reduce("", String::concat).strip();
    }

    private Usage cost(boolean fast, long in, long out, double extra) {
        double usd = fast
                ? (in * fastInputPrice + out * fastOutputPrice) / 1_000_000
                : (in * inputPrice + out * outputPrice) / 1_000_000 + extra;
        return new Usage(fast ? fastModel : model, in, out, usd);
    }

    /** Retry server-side on another model if a safety classifier wrongly declines (e.g. security pages). Not available for Haiku. */
    private static MessageCreateParams.Builder withFallback(MessageCreateParams.Builder builder, String model) {
        if (isHaiku(model)) return builder;
        return builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }

    private static boolean isHaiku(String model) { return model.contains("haiku"); }

    private static AiService.UserFacingException userFacing(AnthropicServiceException e) {
        return new AiService.UserFacingException(switch (e.statusCode()) {
            case 401 -> "Missing or invalid Claude API key. Put ANTHROPIC_API_KEY=... in backend/.env and restart the backend.";
            case 429, 529 -> "Claude is busy or rate-limited. Try again in a minute.";
            default -> "Claude API error (" + e.statusCode() + "). Try again.";
        });
    }

    private static AiService.UserFacingException refused() {
        return new AiService.UserFacingException("Claude declined this request. Try a different part of the page.");
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
