package dev.sushant.samajh.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** Prompt templates in {@code src/main/resources/prompts/*.txt} with {{placeholders}}. */
public final class Prompts {

    public static final String SYSTEM = load("system");

    private Prompts() {}

    public static String render(String name, Map<String, String> values) {
        String out = load(name);
        for (var e : values.entrySet()) out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        return out;
    }

    private static String load(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
