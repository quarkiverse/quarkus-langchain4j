package io.quarkiverse.langchain4j.tavily;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class ShadowSensitiveData {

    private static final int NUMBER_VISIBLE_CHARS = 7;

    static String process(Buffer buffer, String field) {

        final JsonObject bodyJson = buffer.toJsonObject();
        if (bodyJson.containsKey(field)) {

            final String apiKeyField = bodyJson.getString(field);
            String shadowedData;
            if (apiKeyField.length() < NUMBER_VISIBLE_CHARS) {
                shadowedData = "*".repeat(apiKeyField.length());
            } else {
                shadowedData = apiKeyField.substring(0, NUMBER_VISIBLE_CHARS) +
                        "*".repeat(apiKeyField.length() - NUMBER_VISIBLE_CHARS);
            }
            bodyJson.put(field, shadowedData);

        }
        return bodyJson.toString();
    }

}
