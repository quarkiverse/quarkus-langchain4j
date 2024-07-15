package io.quarkiverse.langchain4j.ollama;

public interface OllamaClient {
    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}
