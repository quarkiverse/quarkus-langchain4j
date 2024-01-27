package io.quarkiverse.langchain4j.pinecone.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DescribeIndexResponse {

    private final String name;

    private final DistanceMetric metric;

    private final int dimension;

    private final String host;

    private final IndexStatus status;

    @JsonCreator
    public DescribeIndexResponse(String name, DistanceMetric metric, int dimension, String host, IndexStatus status) {
        this.name = name;
        this.metric = metric;
        this.dimension = dimension;
        this.host = host;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public DistanceMetric getMetric() {
        return metric;
    }

    public int getDimension() {
        return dimension;
    }

    public String getHost() {
        return host;
    }

    public IndexStatus getStatus() {
        return status;
    }
}
