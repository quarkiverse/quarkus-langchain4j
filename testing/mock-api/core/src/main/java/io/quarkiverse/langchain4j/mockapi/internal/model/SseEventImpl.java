package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.SseEvent;

public record SseEventImpl(String event, String data) implements SseEvent {
}
