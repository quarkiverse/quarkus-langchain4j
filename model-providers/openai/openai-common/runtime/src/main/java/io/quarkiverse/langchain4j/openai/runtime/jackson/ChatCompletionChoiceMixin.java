package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ChatCompletionChoice.class)
@JsonDeserialize(builder = ChatCompletionChoice.Builder.class)
public abstract class ChatCompletionChoiceMixin {
}
