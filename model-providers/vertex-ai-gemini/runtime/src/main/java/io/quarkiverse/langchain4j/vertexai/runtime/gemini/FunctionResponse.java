package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

public record FunctionResponse(String name, Response response) {

    public record Response(String name, Object content) {

    }
}
