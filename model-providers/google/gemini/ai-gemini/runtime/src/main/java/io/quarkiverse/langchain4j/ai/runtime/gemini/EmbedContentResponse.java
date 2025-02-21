package io.quarkiverse.langchain4j.ai.runtime.gemini;

public record EmbedContentResponse(Embedding embedding) {
    public record Embedding(float[] values) {
    }
}
