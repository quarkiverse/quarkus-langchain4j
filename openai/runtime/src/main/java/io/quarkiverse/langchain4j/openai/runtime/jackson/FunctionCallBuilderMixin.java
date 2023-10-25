package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.chat.FunctionCall;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(FunctionCall.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class FunctionCallBuilderMixin {
}
