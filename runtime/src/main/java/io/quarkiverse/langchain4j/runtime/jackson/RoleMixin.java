package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.annotation.JsonValue;

import dev.ai4j.openai4j.chat.Role;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(Role.class)
public abstract class RoleMixin {

    @JsonValue
    public String toString() {
        return null;
    }

}
