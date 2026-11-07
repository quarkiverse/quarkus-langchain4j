package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.util.Locale;

import io.quarkiverse.langchain4j.ollama.Role;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class RoleDeserializer extends ValueDeserializer<Role> {

    @Override
    public Role deserialize(JsonParser jp, DeserializationContext deserializationContext) {
        return Role.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
