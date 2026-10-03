package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@JacksonMixin(ToolExecutionRequest.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ToolExecutionRequestBuilderMixin {
}
