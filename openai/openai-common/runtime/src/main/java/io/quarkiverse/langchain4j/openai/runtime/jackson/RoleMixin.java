package io.quarkiverse.langchain4j.openai.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import dev.ai4j.openai4j.chat.Role;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Role.class)
@JsonSerialize(using = RoleSerializer.class)
@JsonDeserialize(using = RoleDeserializer.class)
public abstract class RoleMixin {

}
