package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import io.quarkiverse.langchain4j.ollama.Tool;

public class ToolTypeDeserializer extends StdDeserializer<Tool.Type> {
    public ToolTypeDeserializer() {
        super(Tool.Type.class);
    }

    @Override
    public Tool.Type deserialize(JsonParser jp, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        return Tool.Type.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
