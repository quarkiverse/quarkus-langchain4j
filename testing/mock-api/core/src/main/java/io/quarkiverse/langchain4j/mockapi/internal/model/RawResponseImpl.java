package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.SseEvent;

public record RawResponseImpl(int status, Map<String, String> headers, String body, String bodyBase64, List<SseEvent> events,
        Duration delay) implements RawResponse {

    @Override
    public RawResponse delay(Duration delay) {
        return new RawResponseImpl(status, headers, body, bodyBase64, events, delay);
    }
}
