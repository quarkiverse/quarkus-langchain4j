package io.quarkiverse.langchain4j.ai.runtime.gemini;

public record FunctionResponse(String name, Response response) {

    public record Response(String name, Object content) {

    }
}
