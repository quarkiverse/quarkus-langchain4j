package io.quarkiverse.langchain4j.pinecone.runtime;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a Create index operation against Pinecone.
 * See the <a href="https://docs.pinecone.io/reference/create_index">API documentation</a>.
 * Note that after the successful request, Pinecone takes some time (usually up tens of seconds) for the index to start being
 * usable.
 */
@RegisterForReflection
public class CreateIndexRequest {

    private final String name;
    private final Integer dimension;
    private final DistanceMetric metric;
    private final CreateIndexSpec spec;

    public CreateIndexRequest(String name, Integer dimension, DistanceMetric metric, CreateIndexSpec spec) {
        this.name = name;
        this.dimension = dimension;
        this.metric = metric;
        this.spec = spec;
    }

    public String getName() {
        return name;
    }

    public Integer getDimension() {
        return dimension;
    }

    public DistanceMetric getMetric() {
        return metric;
    }

    public CreateIndexSpec getSpec() {
        return spec;
    }
}
