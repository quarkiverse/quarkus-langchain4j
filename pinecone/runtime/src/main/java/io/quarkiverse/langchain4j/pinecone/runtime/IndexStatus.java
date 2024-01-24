package io.quarkiverse.langchain4j.pinecone.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class IndexStatus {

    private final boolean ready;

    private final String state;

    @JsonCreator
    public IndexStatus(boolean ready, String state) {
        this.ready = ready;
        this.state = state;
    }

    public boolean isReady() {
        return ready;
    }

    public String getState() {
        return state;
    }
}
