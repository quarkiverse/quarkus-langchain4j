package io.quarkiverse.langchain4j.pinecone.runtime;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a query against Pinecone.
 * See the <a href="https://docs.pinecone.io/reference/query">API documentation</a>.
 */
@RegisterForReflection
public class QueryRequest {

    private final String namespace;
    private final Long topK;
    private final boolean includeMetadata;
    private final boolean includeValues;
    private final float[] vector;

    public QueryRequest(String namespace, Long topK, boolean includeMetadata, boolean includeValues, float[] vector) {
        this.namespace = namespace;
        this.topK = topK;
        this.includeMetadata = includeMetadata;
        this.includeValues = includeValues;
        this.vector = vector;
    }

    public String getNamespace() {
        return namespace;
    }

    public Long getTopK() {
        return topK;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public float[] getVector() {
        return vector;
    }

    public boolean isIncludeValues() {
        return includeValues;
    }
}
