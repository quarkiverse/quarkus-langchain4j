package io.quarkiverse.langchain4j.openai.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.ai4j.openai4j.chat.ToolType;

public class ToolTypeDeserializer extends StdDeserializer<ToolType> {
    public ToolTypeDeserializer() {
        super(ToolType.class);
    }

    @Override
    public ToolType deserialize(JsonParser jp, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        return ToolType.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
