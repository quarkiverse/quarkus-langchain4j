package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public record TextExtractionRequest(String spaceId, String projectId, TextExtractionDataReference documentReference,
        TextExtractionDataReference resultsReference, Map<?, ?> assemblyJson, Map<?, ?> assemblyMd, TextExtractionSteps steps) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CosDataLocation(String fileName, String bucket) {
    }

    public record CosDataConnection(String id) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextExtractionStepOcr(List<String> languagesList) {
    }

    public record TextExtractionStepTablesProcessing(boolean enabled) {
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
    public record TextExtractionSteps(TextExtractionStepOcr ocr, TextExtractionStepTablesProcessing tablesProcessing) {
        public static TextExtractionSteps of(List<String> languages, boolean tableProcessing) {
            var obj = new TextExtractionStepTablesProcessing(tableProcessing);
            return new TextExtractionSteps(new TextExtractionStepOcr(languages), obj);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static enum TextExtractionType {

        ASSEMBLY_JSON("assembly_json"),
        ASSEMBLY_MD("assembly_md");

        private final String value;

        TextExtractionType(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public static class Builder {

        private String spaceId;
        private String projectId;
        private TextExtractionDataReference documentReference;
        private TextExtractionDataReference resultsReference;
        private TextExtractionSteps steps;
        private TextExtractionType type = TextExtractionType.ASSEMBLY_JSON;

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

        public Builder steps(TextExtractionSteps steps) {
            this.steps = steps;
            return this;
        }

        public Builder type(TextExtractionType type) {
            this.type = type;
            return this;
        }

        public TextExtractionRequest build() {
            return switch (type) {
                case ASSEMBLY_JSON ->
                    new TextExtractionRequest(spaceId, projectId, documentReference, resultsReference, Collections.emptyMap(),
                            null,
                            steps);
                case ASSEMBLY_MD ->
                    new TextExtractionRequest(spaceId, projectId, documentReference, resultsReference, null,
                            Collections.emptyMap(),
                            steps);
            };
        }
    }
}
