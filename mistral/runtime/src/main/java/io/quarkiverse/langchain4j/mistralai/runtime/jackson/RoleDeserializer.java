package io.quarkiverse.langchain4j.mistralai.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.model.mistralai.MistralAiRole;

public class RoleDeserializer extends StdDeserializer<MistralAiRole> {
    public RoleDeserializer() {
        super(MistralAiRole.class);
    }

    @Override
    public MistralAiRole deserialize(JsonParser jp, DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        return MistralAiRole.valueOf(jp.getValueAsString().toUpperCase(Locale.ROOT));
    }

}
