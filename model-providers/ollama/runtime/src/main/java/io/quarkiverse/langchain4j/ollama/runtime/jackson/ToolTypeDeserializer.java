package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.util.Locale;

import io.quarkiverse.langchain4j.ollama.Tool;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class ToolTypeDeserializer extends ValueDeserializer<Tool.Type> {

    @Override
    public Tool.Type deserialize(JsonParser jp, DeserializationContext deserializationContext) {
        return Tool.Type.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
