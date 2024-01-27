package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a vector passed to the UPSERT operation.
 */
@RegisterForReflection
public class UpsertVector {

    private final String id;
    private final float[] values;
    private final Map<String, String> metadata;

    public UpsertVector(Builder builder) {
        this.id = builder.id;
        this.values = builder.value;
        this.metadata = builder.metadata;
    }

    public String getId() {
        return id;
    }

    public float[] getValues() {
        return values;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public static class Builder {

        private String id = null;
        private float[] value = null;
        private Map<String, String> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder value(float[] value) {
            this.value = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (key != null && value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, String> map) {
            if (map != null) {
                this.metadata.putAll(map);
            }
            return this;
        }

        public UpsertVector build() {
            return new UpsertVector(this);
        }
    }
}
