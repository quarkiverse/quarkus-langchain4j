package io.quarkiverse.langchain4j.mockapi;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

/**
 * A request recorded by the mock server.
 */
public interface CapturedRequest {

    long index();

    String method();

    String path();

    String query();

    Map<String, List<String>> headers();

    /**
     * The request body as text: decoded as UTF-8 for a textual content type (JSON, text, XML, form), otherwise as
     * ISO-8859-1, which maps every byte to one character, so the textual parts of a multipart upload can be asserted on
     * and the bytes recovered with {@code body().getBytes(StandardCharsets.ISO_8859_1)}. Null when the request had no
     * body.
     */
    String body();

    Operation operation();

    String dialect();

    ChatRequestView chat();

    DecisionRequestView decision();

    default String header(String name) {
        if (headers() == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : headers().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    default JsonNode bodyAsJson() {
        try {
            return MockApiJson.mapper().readTree(body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Request body is not valid JSON: " + path(), e);
        }
    }

    default ChatRequestView asChat() {
        if (chat() == null) {
            throw new IllegalStateException("not a chat request: " + path());
        }
        return chat();
    }

    default DecisionRequestView asDecision() {
        if (decision() == null) {
            throw new IllegalStateException("not a decision request: " + path());
        }
        return decision();
    }
}
