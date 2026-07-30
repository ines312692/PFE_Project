package com.elyadata.common.llm;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LlmResponseParser")
class LlmResponseParserTest {


    @Nested
    @DisplayName("null / blank input")
    class NullOrBlank {

        @Test
        @DisplayName("null raw → empty list")
        void null_returnsEmpty() {
            assertThat(LlmResponseParser.parseObjectArray(null)).isEmpty();
        }

        @Test
        @DisplayName("blank string → empty list")
        void blank_returnsEmpty() {
            assertThat(LlmResponseParser.parseObjectArray("   ")).isEmpty();
        }

        @Test
        @DisplayName("no JSON structure → empty list")
        void noJson_returnsEmpty() {
            assertThat(LlmResponseParser.parseObjectArray("no json here")).isEmpty();
        }
    }


    @Nested
    @DisplayName("plain JSON array")
    class PlainArray {

        @Test
        @DisplayName("single-element array → one map")
        void singleElement() {
            String raw = "[{\"name\":\"Alice\",\"age\":30}]";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("name", "Alice");
            assertThat(result.getFirst()).containsEntry("age", 30);
        }

        @Test
        @DisplayName("multi-element array → all maps returned")
        void multipleElements() {
            String raw = "[{\"x\":1},{\"x\":2},{\"x\":3}]";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("array embedded in LLM prose → extracted correctly")
        void arrayWithPreamble() {
            String raw = "Sure! Here is the JSON:\n[{\"key\":\"value\"}]\nDone.";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("array wrapped in markdown code block → extracted correctly")
        void arrayInCodeBlock() {
            String raw = "```json\n[{\"a\":1}]\n```";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("empty array → empty list")
        void emptyArray() {
            assertThat(LlmResponseParser.parseObjectArray("[]")).isEmpty();
        }
    }


    @Nested
    @DisplayName("plain JSON object")
    class PlainObject {

        @Test
        @DisplayName("object without known wrapper key → wrapped in single-element list")
        void objectWithoutWrapperKey() {
            String raw = "{\"name\":\"Bob\",\"score\":99}";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("name", "Bob");
        }

        @Test
        @DisplayName("object with 'data' array key → returns inner list")
        void objectWithDataKey() {
            String raw = "{\"data\":[{\"id\":1},{\"id\":2}]}";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("object with 'results' array key → returns inner list")
        void objectWithResultsKey() {
            String raw = "{\"results\":[{\"id\":\"a\"}]}";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("object with 'items' array key → returns inner list")
        void objectWithItemsKey() {
            String raw = "{\"items\":[{\"v\":1},{\"v\":2},{\"v\":3}]}";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("object with 'records' array key → returns inner list")
        void objectWithRecordsKey() {
            String raw = "{\"records\":[{\"field\":\"val\"}]}";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("field", "val");
        }
    }


    @Nested
    @DisplayName("nested / escaped content")
    class NestedContent {

        @Test
        @DisplayName("JSON with nested object inside array element")
        void nestedObject() {
            String raw = "[{\"meta\":{\"x\":1},\"value\":\"hello\"}]";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().get("meta")).isInstanceOf(Map.class);
        }

        @Test
        @DisplayName("string values with escaped quotes don't break extraction")
        void escapedQuotesInString() {
            String raw = "[{\"text\":\"he said \\\"hello\\\"\"}]";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().get("text").toString()).contains("hello");
        }

        @Test
        @DisplayName("array with brackets inside string values → correct extraction")
        void bracketsInsideStringValues() {
            String raw = "[{\"expr\":\"a[0] + b[1]\"}]";
            List<Map<String, Object>> result = LlmResponseParser.parseObjectArray(raw);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).containsEntry("expr", "a[0] + b[1]");
        }
    }


    @Nested
    @DisplayName("malformed JSON")
    class MalformedJson {

        @Test
        @DisplayName("unclosed array → empty list")
        void unclosedArray() {
            String raw = "[{\"a\":1}";
            assertThat(LlmResponseParser.parseObjectArray(raw)).isEmpty();
        }

        @Test
        @DisplayName("invalid json after array start → empty list")
        void invalidJson() {
            String raw = "[not valid json]";
            assertThat(LlmResponseParser.parseObjectArray(raw)).isEmpty();
        }
    }
}
