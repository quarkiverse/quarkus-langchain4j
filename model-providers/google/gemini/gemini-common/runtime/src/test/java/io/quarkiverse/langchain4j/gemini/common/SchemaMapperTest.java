package io.quarkiverse.langchain4j.gemini.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

class SchemaMapperTest {

    @Test
    void should_map_standard_json_schema() {
        // The top-level schema must have a "content" property to pass isEffectiveEmptyObject()
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("test")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("content", JsonObjectSchema.builder()
                                .addStringProperty("name")
                                .required("name")
                                .build())
                        .required("content")
                        .build())
                .build();

        Schema result = SchemaMapper.fromJsonSchemaToSchema(jsonSchema);

        assertNotNull(result);
        assertEquals(Type.OBJECT, result.getType());
        assertTrue(result.getProperties().containsKey("content"));
        Schema contentSchema = result.getProperties().get("content");
        assertEquals(Type.OBJECT, contentSchema.getType());
        assertTrue(contentSchema.getProperties().containsKey("name"));
    }

    @Test
    void should_return_null_for_null_schema() {
        Schema result = SchemaMapper.fromJsonSchemaToSchema((JsonSchema) null);
        assertNull(result);
    }

    @Test
    void should_map_string_schema() {
        JsonStringSchema stringSchema = JsonStringSchema.builder()
                .description("A name")
                .build();

        Schema result = SchemaMapper.fromJsonSchemaToSchema(stringSchema);

        assertNotNull(result);
        assertEquals(Type.STRING, result.getType());
        assertEquals("A name", result.getDescription());
    }

    @Test
    void should_throw_for_raw_schema_passed_directly() {
        // JsonRawSchema must be handled by BaseGeminiChatModel.detectRawSchema(),
        JsonRawSchema rawSchema = JsonRawSchema.builder()
                .schema("{\"type\": \"object\"}")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> SchemaMapper.fromJsonSchemaToSchema(rawSchema));
    }
}