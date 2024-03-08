package io.quarkiverse.langchain4j.cohere.runtime.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RerankResponse {

    private List<RerankResult> results;
    private Meta meta;

    @JsonCreator
    public RerankResponse(List<RerankResult> results, Meta meta) {
        this.results = results;
        this.meta = meta;
    }

    public List<RerankResult> getResults() {
        return results;
    }

    public Meta getMeta() {
        return meta;
    }
}
