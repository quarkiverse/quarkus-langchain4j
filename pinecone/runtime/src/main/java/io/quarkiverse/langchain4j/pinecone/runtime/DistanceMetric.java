package io.quarkiverse.langchain4j.pinecone.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DistanceMetric {

    @JsonProperty("euclidean")
    EUCLIDEAN,
    @JsonProperty("cosine")
    COSINE,
    @JsonProperty("dotproduct")
    DOTPRODUCT

}
