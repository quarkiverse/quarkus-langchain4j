package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.model.ollama.Role;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Role.class)
@JsonDeserialize(using = RoleDeserializer.class)
@SuppressWarnings("unused")
public class RoleMixin {
}
