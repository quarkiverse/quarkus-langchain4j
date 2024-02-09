package io.quarkiverse.langchain4j.infinispan.runtime;

public class InfinispanSchema {
    private final String cacheName;
    private final Long dimension;
    private final Integer distance;

    public InfinispanSchema(String cacheName,
            Long dimension, Integer distance) {
        this.cacheName = cacheName;
        this.dimension = dimension;
        this.distance = distance;
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
}
