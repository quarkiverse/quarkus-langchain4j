package io.quarkiverse.langchain4j.watsonx.bean;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest.TextExtractionDataReference;

public record TextExtractionResponse(TextExtractionMetadata metadata, Entity entity) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextExtractionMetadata(String id, Instant createdAt, String projectId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ServiceError(String code, String message, String moreInfo) {
    }

    public record Entity(TextExtractionDataReference documentReference, TextExtractionDataReference resultsReference,
            TextExtractionResults results) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TextExtractionResults(Status status, int numberPagesProcessed, Instant runningAt, Instant completedAt,
            int totalPages, ServiceError error) {
    }

    public enum Status {

        @JsonProperty("submitted")
        SUBMITTED,

        @JsonProperty("uploading")
        UPLOADING,

        @JsonProperty("running")
        RUNNING,

        @JsonProperty("downloading")
        DOWNLOADING,

        @JsonProperty("downloaded")
        DOWNLOADED,

        @JsonProperty("completed")
        COMPLETED,

        @JsonProperty("failed")
        FAILED;
    }
}
