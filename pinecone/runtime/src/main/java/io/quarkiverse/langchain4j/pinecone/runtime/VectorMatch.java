package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class VectorMatch {

    private final String id;

    private final float score;

    private final Map<String, String> metadata;

    private final float[] values;

    @JsonCreator
    public VectorMatch(String id, float score, Map<String, String> metadata, float[] values) {
        this.id = id;
        this.score = score;
        this.metadata = metadata;
        this.values = values;
    }

    public String getId() {
        return id;
    }

    public float getScore() {
        return score;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public float[] getValues() {
        return values;
    }
}
