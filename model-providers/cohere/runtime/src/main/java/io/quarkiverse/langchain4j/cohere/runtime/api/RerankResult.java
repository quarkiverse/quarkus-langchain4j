package io.quarkiverse.langchain4j.cohere.runtime.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RerankResult {

    private final Integer index;
    @JsonProperty("relevance_score")
    private final Double relevanceScore;

    @JsonCreator
    public RerankResult(Integer index, Double relevanceScore) {
        this.index = index;
        this.relevanceScore = relevanceScore;
    }

    public Integer getIndex() {
        return index;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }
}
