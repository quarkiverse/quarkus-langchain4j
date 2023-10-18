package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ToolExecutionRequest.class)
@JsonDeserialize(builder = ToolExecutionRequest.Builder.class)
public abstract class ToolExecutionRequestMixin {
}
