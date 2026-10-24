package dev.sushant.samajh.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams AI text to the browser. Events: {@code delta {text}} (repeated), optional named events,
 * then {@code done {}} or {@code error {message}}.
 */
public final class Sse {

    /** What the work gets: accept(text) sends a delta; event(name, data) sends anything else. */
    public interface Out extends Consumer<String> {
        void event(String name, Object data);
    }

    private static final Logger log = LoggerFactory.getLogger(Sse.class);

    private Sse() {}

    /** Runs {@code work} on a virtual thread, streaming what it sends. */
    public static SseEmitter stream(Consumer<Out> work) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Out out = new Out() {
            public void accept(String text) { send(emitter, "delta", Map.of("text", text)); }
            public void event(String name, Object data) { send(emitter, name, data); }
        };
        Thread.ofVirtual().start(() -> {
            try {
                work.accept(out);
                send(emitter, "done", Map.of());
            } catch (UncheckedIOException e) {
                // Browser went away (page changed or tab closed): stop streaming, nothing to report.
            } catch (AiService.UserFacingException e) {
                send(emitter, "error", Map.of("message", e.getMessage()));
            } catch (Exception e) {
                log.error("AI stream failed", e);
                send(emitter, "error", Map.of("message", "Something went wrong. Check the backend log."));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new UncheckedIOException(new IOException("client disconnected", e));
        }
    }
}
