package io.quarkiverse.langchain4j.redis;

/**
 * Similarity metric used by Redis
 */
public enum MetricType {

    /**
     * cosine similarity
     */
    COSINE,

    /**
     * inner product
     */
    IP,

    /**
     * euclidean distance
     */
    L2
}
