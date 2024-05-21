package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import io.quarkiverse.langchain4j.ollama.Role;

public class RoleDeserializer extends StdDeserializer<Role> {
    public RoleDeserializer() {
        super(Role.class);
    }

    @Override
    public Role deserialize(JsonParser jp, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        return Role.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
