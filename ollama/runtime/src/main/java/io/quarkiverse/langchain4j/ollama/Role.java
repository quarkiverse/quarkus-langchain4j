package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.quarkiverse.langchain4j.ollama.runtime.jackson.RoleDeserializer;

@JsonDeserialize(using = RoleDeserializer.class)
public enum Role {

    SYSTEM,
    USER,
    ASSISTANT
}
