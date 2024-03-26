package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Request to generate an embedding model even if there are no
 * non-synthetic injection points for it. This is used by the Easy RAG
 * extension to have an embedding model created automatically.
 */
public final class AutoCreateEmbeddingModelBuildItem extends SimpleBuildItem {

}
