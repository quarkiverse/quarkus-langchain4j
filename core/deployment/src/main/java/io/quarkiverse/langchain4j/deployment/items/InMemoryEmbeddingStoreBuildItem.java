package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker that an in-memory embedding store was registered in the CDI container (by the Easy RAG extension).
 */
public final class InMemoryEmbeddingStoreBuildItem extends SimpleBuildItem {
}
