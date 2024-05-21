package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.model.anthropic.AnthropicContent;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AnthropicContent.class)
@JsonDeserialize(builder = AnthropicContent.AnthropicContentBuilder.class)
public abstract class AnthropicContentMixin {
}
