package io.quarkiverse.langchain4j.gemini.common;

import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class SchemaMapper {

    /**
     * Returns whether the given element and everything nested in it can be mapped to a {@link Schema}.
     * {@code $ref} ({@code JsonReferenceSchema}) and {@code anyOf} ({@code JsonAnyOfSchema}), which LangChain4j
     * generates for recursive and polymorphic types, cannot, and neither can {@code JsonNullSchema}.
     */
    public static boolean isSupported(JsonSchemaElement jsonSchema) {
        if (jsonSchema == null
                || jsonSchema instanceof JsonStringSchema
                || jsonSchema instanceof JsonBooleanSchema
                || jsonSchema instanceof JsonNumberSchema
                || jsonSchema instanceof JsonIntegerSchema
                || jsonSchema instanceof JsonEnumSchema) {
            return true;
        }
        if (jsonSchema instanceof JsonObjectSchema jsonObjectSchema) {
            if (jsonObjectSchema.definitions() != null && !jsonObjectSchema.definitions().isEmpty()) {
                return false;
            }
            if (jsonObjectSchema.properties() != null) {
                for (JsonSchemaElement property : jsonObjectSchema.properties().values()) {
                    if (!isSupported(property)) {
                        return false;
                    }
                }
            }
            return true;
        }
        if (jsonSchema instanceof JsonArraySchema jsonArraySchema) {
            return isSupported(jsonArraySchema.items());
        }
        return false;
    }

    public static Schema fromJsonSchemaToSchema(JsonSchema jsonSchema) {
        if (jsonSchema == null || jsonSchema.rootElement() == null) {
            return null;
        }
        var result = fromJsonSchemaToSchema(jsonSchema.rootElement());
        if ((result == null) || result.isEffectiveEmptyObject()) {
            return null;
        }
        return result;
    }

    static Schema fromJsonSchemaToSchema(JsonSchemaElement jsonSchema) {
        Schema.Builder schemaBuilder = Schema.builder();

        if (jsonSchema instanceof JsonStringSchema jsonStringSchema) {
            schemaBuilder.description(jsonStringSchema.description());
            schemaBuilder.type(Type.STRING);
        } else if (jsonSchema instanceof JsonBooleanSchema jsonBooleanSchema) {
            schemaBuilder.description(jsonBooleanSchema.description());
            schemaBuilder.type(Type.BOOLEAN);
        } else if (jsonSchema instanceof JsonNumberSchema jsonNumberSchema) {
            schemaBuilder.description(jsonNumberSchema.description());
            schemaBuilder.type(Type.NUMBER);
        } else if (jsonSchema instanceof JsonIntegerSchema jsonIntegerSchema) {
            schemaBuilder.description(jsonIntegerSchema.description());
            schemaBuilder.type(Type.INTEGER);
        } else if (jsonSchema instanceof JsonEnumSchema jsonEnumSchema) {
            schemaBuilder.description(jsonEnumSchema.description());
            schemaBuilder.type(Type.STRING);
            schemaBuilder.enumeration(jsonEnumSchema.enumValues());
        } else if (jsonSchema instanceof JsonObjectSchema jsonObjectSchema) {
            schemaBuilder.description(jsonObjectSchema.description());
            schemaBuilder.type(Type.OBJECT);

            if (jsonObjectSchema.properties() != null) {
                Map<String, JsonSchemaElement> properties = jsonObjectSchema.properties();
                Map<String, Schema> mappedProperties = properties.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> fromJsonSchemaToSchema(entry.getValue())));
                schemaBuilder.properties(mappedProperties);
            }

            if (jsonObjectSchema.required() != null) {
                schemaBuilder.required(jsonObjectSchema.required());
            }
        } else if (jsonSchema instanceof JsonArraySchema jsonArraySchema) {
            schemaBuilder.description(jsonArraySchema.description());
            schemaBuilder.type(Type.ARRAY);

            if (jsonArraySchema.items() != null) {
                schemaBuilder.items(fromJsonSchemaToSchema(jsonArraySchema.items()));
            }
        } else {
            throw new IllegalArgumentException("Unsupported JsonSchemaElement type: " + jsonSchema.getClass());
        }

        return schemaBuilder.build();
    }
}
