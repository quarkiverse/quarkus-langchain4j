package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.model.anthropic.AnthropicRole;

public class AnthropicRoleSerializer extends StdSerializer<AnthropicRole> {
    public AnthropicRoleSerializer() {
        super(AnthropicRole.class);
    }

    @Override
    public void serialize(AnthropicRole value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString().toLowerCase(Locale.ROOT));
    }
}
