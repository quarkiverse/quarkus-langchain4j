package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.util.Locale;

import io.quarkiverse.langchain4j.ollama.Role;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class RoleSerializer extends ValueSerializer<Role> {

    @Override
    public void serialize(Role role, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
        jsonGenerator.writeString(role.toString().toLowerCase(Locale.ROOT));
    }
}
