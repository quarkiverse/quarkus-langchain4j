package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PredictRequest(List<Instance> instances, Parameters parameters) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Instance(String content, @JsonProperty("task_type") String taskType, String title) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Parameters(Boolean autoTruncate, @JsonProperty("outputDimensionality") Integer outputDimensionality) {
    }
}
