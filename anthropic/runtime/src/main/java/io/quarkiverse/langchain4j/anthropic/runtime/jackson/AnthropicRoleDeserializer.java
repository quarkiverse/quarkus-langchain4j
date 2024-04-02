package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.model.anthropic.AnthropicRole;

public class AnthropicRoleDeserializer extends StdDeserializer<AnthropicRole> {
    public AnthropicRoleDeserializer() {
        super(AnthropicRole.class);
    }

    @Override
    public AnthropicRole deserialize(JsonParser jp, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        return AnthropicRole.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
