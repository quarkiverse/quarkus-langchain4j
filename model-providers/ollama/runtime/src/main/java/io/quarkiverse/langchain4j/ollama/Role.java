package io.quarkiverse.langchain4j.ollama;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.quarkiverse.langchain4j.ollama.runtime.jackson.RoleDeserializer;
import io.quarkiverse.langchain4j.ollama.runtime.jackson.RoleSerializer;

@JsonDeserialize(using = RoleDeserializer.class)
@JsonSerialize(using = RoleSerializer.class)
public enum Role {

    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
