package dev.sushant.samajh.ai;

import java.math.BigDecimal;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The only entry point for AI: Gemini (free tier), Claude (paid) or a local model (Ollama, free), chosen in Settings.
 * Logs token usage and enforces the monthly budget (Claude only; the others cost nothing).
 */
@Service
public class AiService {

    /** An error whose message is safe and useful to show in the UI. */
    public static class UserFacingException extends RuntimeException {
        public UserFacingException(String message) { super(message); }
    }

    public record Result(String text, String stopReason) {}

    /** How hard the model should think: LOW for explanations, MEDIUM for deeper work. */
    public enum Effort { LOW, MEDIUM }

    /** The single row of the settings table. */
    public record Settings(String provider, String localModel, double monthlyBudgetUsd) {}

    private static final String OCR_PROMPT = """
            Transcribe all the text on this book page exactly as printed, keeping paragraphs and line breaks.
            Keep Devanagari (Marathi/Hindi) in Devanagari Unicode. Keep code exactly. Output only the page text, nothing else.""";

    private final JdbcTemplate jdbc;
    private final ClaudeClient claude;
    private final GeminiClient gemini;
    private final LocalClient local;

    AiService(JdbcTemplate jdbc, ClaudeClient claude, GeminiClient gemini, LocalClient local) {
        this.jdbc = jdbc;
        this.claude = claude;
        this.gemini = gemini;
        this.local = local;
    }

    public Settings settings() {
        return jdbc.queryForObject("SELECT provider, local_model, monthly_budget_usd FROM settings WHERE id = 1",
                (rs, i) -> new Settings(rs.getString(1), rs.getString(2), rs.getDouble(3)));
    }

    /** The model answering right now (stored next to cached results). */
    public String model() { return provider().model(); }

    public double spentThisMonth() {
        BigDecimal spent = jdbc.queryForObject(
                "SELECT coalesce(sum(cost_usd), 0) FROM ai_usage WHERE created_at >= date_trunc('month', now())",
                BigDecimal.class);
        return spent == null ? 0 : spent.doubleValue();
    }

    /** Gemini calls made today (free-tier limits are per day). */
    public long geminiRequestsToday() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ai_usage WHERE model LIKE 'gemini%' AND created_at >= date_trunc('day', now())", Long.class);
        return n == null ? 0 : n;
    }

    public double monthlyBudget() { return settings().monthlyBudgetUsd(); }

    /** Streams the model's text to {@code onText} and returns the full text. {@code onText} may throw to abort. */
    public Result stream(String feature, String system, String user, Effort effort, Consumer<String> onText) {
        return provider().stream(system, user, effort, onText, logUsage(feature));
    }

    /** One non-streaming call whose answer is parsed into {@code type} (JSON schema derived from the record). */
    public <T> T structured(String feature, String system, String user, Class<T> type) {
        return structured(feature, system, user, type, false);
    }

    /** As above; {@code fast} uses the cheaper model where the provider has one (bulk generation such as practice questions). */
    public <T> T structured(String feature, String system, String user, Class<T> type, boolean fast) {
        return provider().structured(system, user, type, fast, logUsage(feature));
    }

    /** Whether the current provider can do web search (for "Beyond the PDF"). */
    public boolean canSearch() { return provider().canSearch(); }

    /** Web-search answer limited to trusted sites; every statement has a source. */
    public SourcedText webSearch(String feature, String system, String user) {
        return provider().webSearch(system, user, logUsage(feature));
    }

    /** Reads a page image (PNG) and returns its text. */
    public String ocr(byte[] png) {
        return provider().ocr(png, OCR_PROMPT, logUsage("ocr"));
    }

    /** The provider from Settings; refuses Claude calls once the monthly budget is spent. */
    private AiProvider provider() {
        Settings s = settings();
        if ("gemini".equals(s.provider())) return gemini;
        if ("local".equals(s.provider())) return local;
        if (spentThisMonth() >= s.monthlyBudgetUsd()) {
            throw new UserFacingException("Monthly Claude budget of $%.0f reached. Raise it in Settings, or switch to Gemini or Local (free)."
                    .formatted(s.monthlyBudgetUsd()));
        }
        return claude;
    }

    private Consumer<AiProvider.Usage> logUsage(String feature) {
        return u -> jdbc.update("INSERT INTO ai_usage (feature, model, input_tokens, output_tokens, cost_usd) VALUES (?, ?, ?, ?, ?)",
                feature, u.model(), u.inputTokens(), u.outputTokens(), u.costUsd());
    }
}
