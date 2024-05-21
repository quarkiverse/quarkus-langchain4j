package io.quarkiverse.langchain4j.pinecone.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a response to an Upsert operation.
 * See the <a href="https://docs.pinecone.io/reference/query">API documentation</a>.
 */
@RegisterForReflection
public class UpsertResponse {

    private final long upsertedCount;

    @JsonCreator
    public UpsertResponse(long upsertedCount) {
        this.upsertedCount = upsertedCount;
    }

    public long getUpsertedCount() {
        return upsertedCount;
    }
}
