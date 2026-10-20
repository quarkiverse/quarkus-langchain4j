package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

final class OpenAiTestRequests {

    private OpenAiTestRequests() {
    }

    static CapturedRequest req(String path, Operation op, String body) {
        return new CapturedRequestImpl(1, "POST", path, null, Map.of(), body, op, "openai", null, null);
    }

    static Dialect.Match match(Operation op, boolean streaming, String variant) {
        return new Dialect.Match(op, streaming, variant);
    }
}
