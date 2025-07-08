package io.quarkiverse.langchain4j.mcp.runtime.devui.json;

import java.util.stream.Collectors;

import dev.langchain4j.model.chat.request.json.*;

public class JsonSchemaToExampleStringHelper {

    public static String generateExampleStringFromSchema(JsonSchemaElement element) {
        if (element instanceof JsonBooleanSchema) {
            return "true";
        } else if (element instanceof JsonNumberSchema) {
            return "1.0";
        } else if (element instanceof JsonStringSchema) {
            return "\"example\"";
        } else if (element instanceof JsonIntegerSchema) {
            return "1";
        } else if (element instanceof JsonNullSchema) {
            return "null";
        } else if (element instanceof JsonObjectSchema schema) {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            String example = schema.properties().entrySet().stream()
                    .map((entry) -> "\"" + entry.getKey() + "\": " + generateExampleStringFromSchema(entry.getValue()))
                    .collect(Collectors.joining(", "));
            builder.append(example);
            builder.append("}");
            return builder.toString();
        } else if (element instanceof JsonArraySchema schema) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            builder.append(generateExampleStringFromSchema(schema.items()));
            builder.append("]");
            return builder.toString();
        } else if (element instanceof JsonAnyOfSchema schema) {
            return generateExampleStringFromSchema(schema.anyOf().get(0));
        }
        throw new UnsupportedOperationException("Unsupported schema type: " + element.getClass());
    }
}
