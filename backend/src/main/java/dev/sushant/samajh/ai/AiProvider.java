package dev.sushant.samajh.ai;

import java.util.function.Consumer;

/** One AI backend (Claude or Gemini). {@link AiService} picks one from Settings and logs the reported usage. */
interface AiProvider {

    /** Tokens and cost of one model call; {@code costUsd} is 0 on Gemini's free tier. */
    record Usage(String model, long inputTokens, long outputTokens, double costUsd) {}

    String model();

    /** Streams text to {@code onText} (which may throw to abort); stop reason is normalised to end_turn / max_tokens. */
    AiService.Result stream(String system, String user, AiService.Effort effort, Consumer<String> onText, Consumer<Usage> usage);

    /** One answer parsed into the record {@code type}; {@code fast} = cheaper model for bulk generation. */
    <T> T structured(String system, String user, Class<T> type, boolean fast, Consumer<Usage> usage);

    /** Web-search answer where every statement has a source on a trusted domain. */
    SourcedText webSearch(String system, String user, Consumer<Usage> usage);

    /** Text of a page image (PNG). */
    String ocr(byte[] png, String prompt, Consumer<Usage> usage);
}
