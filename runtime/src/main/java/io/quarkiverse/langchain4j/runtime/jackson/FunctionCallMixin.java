package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.FunctionCall;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(FunctionCall.class)
@JsonDeserialize(builder = FunctionCall.Builder.class)
public abstract class FunctionCallMixin {
}
