package io.quarkiverse.langchain4j.ollama;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class FormatJsonSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null)
            return;
        else if (value.startsWith("{") && value.endsWith("}"))
            gen.writeRawValue(value);
        else
            gen.writeString(value);
    }
}
