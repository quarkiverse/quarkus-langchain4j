package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents an upsert operation against Pinecone.
 * See the <a href="https://docs.pinecone.io/reference/upsert">API documentation</a>.
 */
@RegisterForReflection
public class UpsertRequest {

    private final List<UpsertVector> vectors;

    private final String namespace;

    public UpsertRequest(List<UpsertVector> vectors, String namespace) {
        this.vectors = vectors;
        this.namespace = namespace;
    }

    public List<UpsertVector> getVectors() {
        return vectors;
    }
}
