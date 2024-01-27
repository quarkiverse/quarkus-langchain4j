package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.chat.ToolMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ToolMessage.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class ToolMessageBuilderMixin {
}
