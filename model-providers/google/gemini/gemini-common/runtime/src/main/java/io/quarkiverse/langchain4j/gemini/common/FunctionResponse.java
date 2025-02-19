package io.quarkiverse.langchain4j.gemini.common;

public record FunctionResponse(String name, Response response) {

    public record Response(String name, Object content) {

    }
}
