package dev.sushant.samajh.ai;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON schema for an answer record, for Gemini's {@code responseJsonSchema} (Claude's SDK derives its own).
 * Covers what our answer records use: String, int/long/double/boolean, enums, lists and nested records.
 */
final class RecordSchema {

    private RecordSchema() {}

    static Map<String, Object> of(Class<?> record) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (RecordComponent c : record.getRecordComponents()) properties.put(c.getName(), type(c.getGenericType()));
        return Map.of("type", "object", "properties", properties,
                "required", Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList(),
                "additionalProperties", false);
    }

    /**
     * Throws if a parsed answer is missing fields (null), also inside lists of records.
     * Small models sometimes drop or rename a field; callers treat this like malformed JSON and retry.
     */
    static <T> T requireComplete(T value) {
        if (value == null) throw new IllegalArgumentException("empty answer");
        if (!value.getClass().isRecord()) return value;
        for (RecordComponent c : value.getClass().getRecordComponents()) {
            Object v;
            try {
                var accessor = c.getAccessor();
                accessor.setAccessible(true);   // answer records are package-private in other packages
                v = accessor.invoke(value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (v == null) throw new IllegalArgumentException("missing field: " + c.getName());
            if (v instanceof List<?> list) list.forEach(RecordSchema::requireComplete);
            else requireComplete(v);
        }
        return value;
    }

    private static Map<String, Object> type(Type t) {
        if (t instanceof ParameterizedType p && p.getRawType() == List.class) {
            return Map.of("type", "array", "items", type(p.getActualTypeArguments()[0]));
        }
        Class<?> c = (Class<?>) t;
        if (c == String.class) return Map.of("type", "string");
        if (c == int.class || c == Integer.class || c == long.class || c == Long.class) return Map.of("type", "integer");
        if (c == double.class || c == Double.class) return Map.of("type", "number");
        if (c == boolean.class || c == Boolean.class) return Map.of("type", "boolean");
        if (c.isEnum()) return Map.of("type", "string", "enum", Arrays.stream(c.getEnumConstants()).map(Object::toString).toList());
        if (c.isRecord()) return of(c);
        throw new IllegalArgumentException("Unsupported type in answer record: " + c);
    }
}
