package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;

public record CapturedRequestImpl(long index, String method, String path, String query, Map<String, List<String>> headers,
        String body, Operation operation, String dialect, ChatRequestView chat,
        DecisionRequestView decision) implements CapturedRequest {
}
