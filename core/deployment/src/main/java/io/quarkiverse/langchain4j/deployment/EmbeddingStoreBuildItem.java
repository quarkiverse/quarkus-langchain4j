package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Marker that an embedding store was registered in the CDI container.
 * <p>
 * When the store has a configurable dimension, the {@code dimensionConfigProperty}
 * carries the property name of that dimension (or {@code dimension} carries the value when the dimension is
 * only known at build time) so that a warning can be emitted at startup when it does not match the dimension
 * of the {@code EmbeddingModel}.
 */
public final class EmbeddingStoreBuildItem extends MultiBuildItem {

    private final String storeName;
    private final String dimensionConfigProperty;
    private final Integer dimension;

    public EmbeddingStoreBuildItem() {
        this(null, null, null);
    }

    public EmbeddingStoreBuildItem(String storeName, String dimensionConfigProperty) {
        this(storeName, dimensionConfigProperty, null);
    }

    public EmbeddingStoreBuildItem(String storeName, String dimensionConfigProperty, Integer dimension) {
        this.storeName = storeName;
        this.dimensionConfigProperty = dimensionConfigProperty;
        this.dimension = dimension;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getDimensionConfigProperty() {
        return dimensionConfigProperty;
    }

    public Integer getDimension() {
        return dimension;
    }
}
