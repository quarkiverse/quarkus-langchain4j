package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.util.List;

/**
 * Used for batch embedding request
 */
public record EmbedContentRequests(List<EmbedContentRequest> requests) {
}
