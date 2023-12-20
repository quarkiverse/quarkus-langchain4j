package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Marker that an embedding model was registered in the CDI container. This is used
 * specifically by the Dev UI processor to decide whether to add the embedding store page.
 */
public final class EmbeddingModelBuildItem extends MultiBuildItem {
}
