package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ToolExecutionRequest.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ToolExecutionRequestBuilderMixin {
}
