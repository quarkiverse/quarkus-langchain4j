package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.RawResponse;

/**
 * A raw stub as sent to the admin API: the requests it matches, and the responses it sends in order, the last one
 * repeating.
 */
public record RawStub(String method, String pathGlob, String bodyJsonPointer, String bodyExpected,
        List<RawResponse> responses) {
}
