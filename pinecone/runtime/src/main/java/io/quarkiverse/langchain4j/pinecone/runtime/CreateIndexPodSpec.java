package io.quarkiverse.langchain4j.pinecone.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CreateIndexPodSpec {

    private final String environment;

    @JsonProperty("pod_type")
    private final String podType;

    public CreateIndexPodSpec(String environment, String podType) {
        this.environment = environment;
        this.podType = podType;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getPodType() {
        return podType;
    }
}
