package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PredictRequest {

    private final List<Instance> instances;
    private final Parameters parameters;

    public PredictRequest(List<Instance> instances, Parameters parameters) {
        this.instances = instances;
        this.parameters = parameters;
    }

    public List<Instance> getInstances() {
        return instances;
    }

    public Parameters getParameters() {
        return parameters;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Instance {
        private final String content;
        @JsonProperty("task_type")
        private final String taskType;
        private final String title;

        public Instance(String content, String taskType, String title) {
            this.content = content;
            this.taskType = taskType;
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public String getTaskType() {
            return taskType;
        }

        public String getTitle() {
            return title;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameters {
        private final Boolean autoTruncate;
        @JsonProperty("outputDimensionality")
        private final Integer outputDimensionality;

        public Parameters(Boolean autoTruncate, Integer outputDimensionality) {
            this.autoTruncate = autoTruncate;
            this.outputDimensionality = outputDimensionality;
        }

        public Boolean getAutoTruncate() {
            return autoTruncate;
        }

        public Integer getOutputDimensionality() {
            return outputDimensionality;
        }
    }
}
