package dev.sushant.samajh.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordSchemaTest {

    enum Kind { MCQ, EXPLAIN }
    record Item(Kind type, String prompt, List<String> options, int answerIndex) {}
    record Set(List<Item> questions) {}

    @Test
    void describesNestedRecordsListsAndEnums() {
        Map<String, Object> schema = RecordSchema.of(Set.class);
        assertEquals(List.of("questions"), schema.get("required"));
        @SuppressWarnings("unchecked")
        var item = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("questions")).get("items");
        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) item.get("properties");
        assertEquals(Map.of("type", "string", "enum", List.of("MCQ", "EXPLAIN")), props.get("type"));
        assertEquals(Map.of("type", "array", "items", Map.of("type", "string")), props.get("options"));
        assertEquals(Map.of("type", "integer"), props.get("answerIndex"));
        assertEquals(List.of("type", "prompt", "options", "answerIndex"), item.get("required"));
    }
}
