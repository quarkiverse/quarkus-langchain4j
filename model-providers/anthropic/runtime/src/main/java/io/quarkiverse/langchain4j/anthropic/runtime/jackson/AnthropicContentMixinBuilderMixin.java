package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import dev.langchain4j.model.anthropic.AnthropicContent;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AnthropicContent.AnthropicContentBuilder.class)
@JsonPOJOBuilder(withPrefix = "")
public abstract class AnthropicContentMixinBuilderMixin {
}
