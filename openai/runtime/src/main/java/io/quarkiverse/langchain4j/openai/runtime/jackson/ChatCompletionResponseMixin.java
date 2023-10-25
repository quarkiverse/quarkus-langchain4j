package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ChatCompletionResponse.class)
@JsonDeserialize(builder = ChatCompletionResponse.Builder.class)
public abstract class ChatCompletionResponseMixin {
}
