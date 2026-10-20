package io.quarkiverse.langchain4j.mockapi;

import io.quarkiverse.langchain4j.mockapi.internal.model.SseEventImpl;

public interface SseEvent {

    static SseEvent of(String event, String data) {
        return new SseEventImpl(event, data);
    }

    String event();

    String data();
}
