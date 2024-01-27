package io.quarkiverse.langchain4j.chroma.runtime;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Collection {

    private final String id;
    private final String name;
    private final Map<String, String> metadata;

    @JsonCreator
    public Collection(String id, String name, Map<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
