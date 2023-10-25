package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.FunctionCall;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(FunctionCall.class)
@JsonDeserialize(builder = FunctionCall.Builder.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class FunctionCallMixin {
}
