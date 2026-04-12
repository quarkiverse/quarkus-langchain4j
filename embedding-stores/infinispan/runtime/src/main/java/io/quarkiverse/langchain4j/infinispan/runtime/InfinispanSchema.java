package io.quarkiverse.langchain4j.infinispan.runtime;

/**
 * Holds the runtime configuration values used by the Infinispan embedding store,
 * such as cache name, vector dimension, distance, similarity metric, and cache creation settings.
 */
public class InfinispanSchema {
    private final String cacheName;
    private final Long dimension;
    private final Integer distance;
    private final String similarity;
    private final boolean createCache;
    private final String cacheConfig;

    public InfinispanSchema(String cacheName, Long dimension, Integer distance,
            String similarity, boolean createCache, String cacheConfig) {
        this.cacheName = cacheName;
        this.dimension = dimension;
        this.distance = distance;
        this.similarity = similarity;
        this.createCache = createCache;
        this.cacheConfig = cacheConfig;
    }

    public String getCacheName() {
        return cacheName;
    }

    public Long getDimension() {
        return dimension;
    }

    public Integer getDistance() {
        return distance;
    }

    public String getSimilarity() {
        return similarity;
    }

    public boolean isCreateCache() {
        return createCache;
    }

    public String getCacheConfig() {
        return cacheConfig;
    }
}
