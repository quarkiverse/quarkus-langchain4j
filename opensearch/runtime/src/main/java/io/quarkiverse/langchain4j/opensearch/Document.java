package io.quarkiverse.langchain4j.opensearch;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonDeserialize(builder = Document.Builder.class)
class Document {

    private float[] vector;
    private String text;
    private Map<String, String> metadata;

    private Document(Builder builder) {
        this.vector = builder.vector;
        this.text = builder.text;
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private float[] vector;
        private String text;
        private Map<String, String> metadata;

        private Builder() {

        }

        public Builder vector(float[] val) {
            vector = val;
            return this;
        }

        public Builder text(String val) {
            text = val;
            return this;
        }

        public Builder metadata(Map<String, String> val) {
            metadata = val;
            return this;
        }

        public Document build() {
            return new Document(this);
        }
    }
}
