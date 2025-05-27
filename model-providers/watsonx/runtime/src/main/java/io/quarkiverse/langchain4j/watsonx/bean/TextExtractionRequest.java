package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public record TextExtractionRequest(String spaceId, String projectId, TextExtractionDataReference documentReference,
        TextExtractionDataReference resultsReference, TextExtractionParameters parameters) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CosDataLocation(String fileName, String bucket) {
    }

    public record CosDataConnection(String id) {
    }

    public record TextExtractionDataReference(String type, CosDataConnection connection, CosDataLocation location) {
        public static TextExtractionDataReference of(String connectionId, String fileName) {
            return new TextExtractionDataReference("connection_asset", new CosDataConnection(connectionId),
                    new CosDataLocation(fileName, null));
        }

        public static TextExtractionDataReference of(String connectionId, String fileName, String bucket) {
            return new TextExtractionDataReference("connection_asset", new CosDataConnection(connectionId),
                    new CosDataLocation(fileName, bucket));
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextExtractionParameters(
            List<Type> requestedOutputs, Mode mode, OCR ocrMode,
            Boolean autoRotationCorrection, EmbeddedImages createEmbeddedImages,
            Integer outputDpi, Boolean outputTokensAndBbox) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static enum Type {

        @JsonProperty("assembly")
        JSON,

        @JsonProperty("html")
        HTML,

        @JsonProperty("md")
        MD,

        @JsonProperty("plain_text")
        PLAIN_TEXT,

        @JsonProperty("page_images")
        PAGE_IMAGES;
    }

    public static enum Mode {

        @JsonProperty("standard")
        STANDARD,

        @JsonProperty("high_quality")
        HIGH_QUALITY;
    }

    public static enum OCR {

        @JsonProperty("disabled")
        DISABLED,

        @JsonProperty("enabled")
        ENABLED,

        @JsonProperty("forced")
        FORCED;
    }

    public static enum EmbeddedImages {

        @JsonProperty("disabled")
        DISABLED,

        @JsonProperty("enabled_placeholder")
        ENABLED_PLACEHOLDER,

        @JsonProperty("enabled_text")
        ENABLED_TEXT,

        @JsonProperty("enabled_verbalization")
        ENABLED_VERBALIZATION,

        @JsonProperty("enabled_verbalization_all")
        ENABLED_VERBALIZATION_ALL;
    }

    public static class Builder {

        private String spaceId;
        private String projectId;
        private TextExtractionDataReference documentReference;
        private TextExtractionDataReference resultsReference;
        private TextExtractionParameters parameters;

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder documentReference(TextExtractionDataReference documentReference) {
            this.documentReference = documentReference;
            return this;
        }

        public Builder resultsReference(TextExtractionDataReference resultsReference) {
            this.resultsReference = resultsReference;
            return this;
        }

        public Builder parameters(TextExtractionParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public TextExtractionRequest build() {
            return new TextExtractionRequest(spaceId, projectId, documentReference, resultsReference, parameters);
        }
    }
}
