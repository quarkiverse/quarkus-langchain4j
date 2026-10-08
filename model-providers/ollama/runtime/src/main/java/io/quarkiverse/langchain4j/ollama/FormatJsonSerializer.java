package io.quarkiverse.langchain4j.ollama;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class FormatJsonSerializer extends ValueSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null)
            return;
        else if (value.startsWith("{") && value.endsWith("}"))
            gen.writeRawValue(value);
        else
            gen.writeString(value);
    }
}
