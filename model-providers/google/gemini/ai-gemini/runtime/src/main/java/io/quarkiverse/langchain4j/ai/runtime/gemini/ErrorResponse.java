package io.quarkiverse.langchain4j.ai.runtime.gemini;

public record ErrorResponse(ErrorInfo error) {
    public record ErrorInfo(Integer code, String message, String status) {
    }
}
