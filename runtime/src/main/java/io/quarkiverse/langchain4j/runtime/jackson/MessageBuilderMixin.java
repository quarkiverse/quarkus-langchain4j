package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.chat.Message;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Message.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class MessageBuilderMixin {
}
