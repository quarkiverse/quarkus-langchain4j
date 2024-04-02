package io.quarkiverse.langchain4j.anthropic.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.model.anthropic.AnthropicMessage;

public class AnthropicMessageSerializer extends StdSerializer<AnthropicMessage> {
    public AnthropicMessageSerializer() {
        super(AnthropicMessage.class);
    }

    @Override
    public void serialize(AnthropicMessage value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString().toLowerCase(Locale.ROOT));
    }
}
