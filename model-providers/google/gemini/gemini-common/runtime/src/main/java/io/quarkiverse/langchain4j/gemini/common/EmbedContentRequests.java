package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

/**
 * Used for batch embedding request
 */
public record EmbedContentRequests(List<EmbedContentRequest> requests) {
}
