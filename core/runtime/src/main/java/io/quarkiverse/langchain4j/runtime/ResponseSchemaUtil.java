package io.quarkiverse.langchain4j.runtime;

public class ResponseSchemaUtil {

    private static final String RESPONSE_SCHEMA = "response_schema";
    private static final String RESPONSE_SCHEMA_PLACEHOLDER = "%s%s%s".formatted("{", RESPONSE_SCHEMA, "}");

    public static String placeholder() {
        return RESPONSE_SCHEMA_PLACEHOLDER;
    }

    public static String templateParam() {
        return RESPONSE_SCHEMA;
    }

    public static boolean hasResponseSchema(String templateText) {
        return templateText.contains(RESPONSE_SCHEMA_PLACEHOLDER);
    }
}
