package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.completion.CompletionResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(CompletionResponse.class)
@JsonDeserialize(builder = CompletionResponse.Builder.class)
public abstract class CompletionResponseMixin {
}
