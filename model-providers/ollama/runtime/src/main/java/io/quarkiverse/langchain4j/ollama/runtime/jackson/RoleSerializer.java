package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.quarkiverse.langchain4j.ollama.Role;

public class RoleSerializer extends StdSerializer<Role> {
    public RoleSerializer() {
        super(Role.class);
    }

    @Override
    public void serialize(Role role, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(role.toString().toLowerCase(Locale.ROOT));
    }
}
