package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.internal.model.RawResponseImpl;

public non-sealed interface RawResponse extends Reply {

    static RawResponse json(int status, String body) {
        return of(status, Map.of("Content-Type", "application/json"), body);
    }

    static RawResponse of(int status, Map<String, String> headers, String body) {
        return new RawResponseImpl(status, headers, body, null, null, null);
    }

    static RawResponse sse(int status, Map<String, String> headers, List<SseEvent> events) {
        return new RawResponseImpl(status, headers, null, null, events, null);
    }

    int status();

    Map<String, String> headers();

    String body();

    String bodyBase64();

    List<SseEvent> events();

    RawResponse delay(Duration delay);
}
