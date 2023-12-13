package io.quarkiverse.langchain4j.openai.runtime.jackson;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.ai4j.openai4j.chat.ToolCall;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AssistantMessage.Builder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class AssistantMessageBuilderMixin {

    @JsonSetter
    public AssistantMessage.Builder toolCalls(List<ToolCall> toolCalls) {
        return null;
    }
}
