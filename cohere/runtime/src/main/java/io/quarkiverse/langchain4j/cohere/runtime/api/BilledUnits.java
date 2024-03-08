package io.quarkiverse.langchain4j.cohere.runtime.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BilledUnits {

    @JsonProperty("search_units")
    private final Integer searchUnits;

    @JsonCreator
    public BilledUnits(Integer searchUnits) {
        this.searchUnits = searchUnits;
    }

    public Integer getSearchUnits() {
        return searchUnits;
    }
}
