package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ChatCompletionRequest.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class ChatCompletionRequestMixin {
}
