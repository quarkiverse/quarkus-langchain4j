package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.model.anthropic.AnthropicMessage;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AnthropicMessage.class)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
@JsonDeserialize(builder = AnthropicMessage.AnthropicMessageBuilder.class)
public abstract class AnthropicMessageMixin {
}
