package io.quarkiverse.langchain4j.cohere.runtime.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Meta {

    @JsonProperty("billed_units")
    private final BilledUnits billedUnits;

    @JsonCreator
    public Meta(BilledUnits billedUnits) {
        this.billedUnits = billedUnits;
    }

    public BilledUnits getBilledUnits() {
        return billedUnits;
    }
}
