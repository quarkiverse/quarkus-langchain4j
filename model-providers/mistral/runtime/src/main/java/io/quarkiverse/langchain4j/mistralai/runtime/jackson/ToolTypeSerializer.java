package io.quarkiverse.langchain4j.mistralai.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.model.mistralai.MistralAiToolType;

public class ToolTypeSerializer extends StdSerializer<MistralAiToolType> {
    public ToolTypeSerializer() {
        super(MistralAiToolType.class);
    }

    @Override
    public void serialize(MistralAiToolType value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString().toLowerCase(Locale.ROOT));
    }
}
