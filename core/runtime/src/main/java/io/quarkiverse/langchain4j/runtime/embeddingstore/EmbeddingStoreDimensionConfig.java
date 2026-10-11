package io.quarkiverse.langchain4j.runtime.embeddingstore;

/**
 * Holds the runtime information of a registered embedding store that has a configurable dimension.
 *
 * @param storeName the name of the store, {@code null} when the store uses the default (unnamed) configuration
 * @param dimensionConfigProperty the config property that carries the dimension of the store, may be {@code null}
 *        when the dimension is only known at build time
 * @param dimension the dimension of the store when only known at build time, {@code null} otherwise
 */
public final class EmbeddingStoreDimensionConfig {

    private final String storeName;
    private final String dimensionConfigProperty;
    private final Integer dimension;

    public EmbeddingStoreDimensionConfig(String storeName, String dimensionConfigProperty, Integer dimension) {
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
