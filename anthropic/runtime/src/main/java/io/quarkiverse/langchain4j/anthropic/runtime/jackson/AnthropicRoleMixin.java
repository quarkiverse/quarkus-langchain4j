package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.langchain4j.model.anthropic.AnthropicRole;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(AnthropicRole.class)
@JsonSerialize(using = AnthropicRoleSerializer.class)
@JsonDeserialize(using = AnthropicRoleDeserializer.class)
public abstract class AnthropicRoleMixin {

}
