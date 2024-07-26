package io.quarkiverse.langchain4j.ollama.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.quarkiverse.langchain4j.ollama.Tool;

public class ToolTypeSerializer extends StdSerializer<Tool.Type> {
    public ToolTypeSerializer() {
        super(Tool.Type.class);
    }

    @Override
    public void serialize(Tool.Type toolType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeString(toolType.toString().toLowerCase(Locale.ROOT));
    }
}
