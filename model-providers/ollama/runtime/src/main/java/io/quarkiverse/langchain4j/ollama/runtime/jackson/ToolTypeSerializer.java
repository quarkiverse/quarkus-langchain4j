package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.util.Locale;

import io.quarkiverse.langchain4j.ollama.Tool;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ToolTypeSerializer extends ValueSerializer<Tool.Type> {

    @Override
    public void serialize(Tool.Type toolType, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
        jsonGenerator.writeString(toolType.toString().toLowerCase(Locale.ROOT));
    }
}
