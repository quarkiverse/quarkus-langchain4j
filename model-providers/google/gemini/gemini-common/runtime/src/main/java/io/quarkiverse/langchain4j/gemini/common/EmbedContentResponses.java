package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

/**
 * Used for batch embeddings response.
 */
public record EmbedContentResponses(List<EmbedContentResponse.Embedding> embeddings) {
}
