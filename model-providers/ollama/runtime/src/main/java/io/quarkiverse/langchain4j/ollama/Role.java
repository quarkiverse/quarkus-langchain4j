package io.quarkiverse.langchain4j.ollama;

import io.quarkiverse.langchain4j.ollama.runtime.jackson.RoleDeserializer;
import io.quarkiverse.langchain4j.ollama.runtime.jackson.RoleSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@JsonDeserialize(using = RoleDeserializer.class)
@JsonSerialize(using = RoleSerializer.class)
public enum Role {

    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
