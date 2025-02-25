package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.util.List;

public record EmbedContentResponses(List<EmbedContentResponse.Embedding> embeddings) {
}
