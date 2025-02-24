package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;

/**
 * Often LLMs return a date as a JSON object containing the date's constituents
 */
public class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            int year = node.get("year").asInt();
            int month = node.get("month").asInt();
            int day = node.get("day").asInt();
            return LocalDate.of(year, month, day);
        } else {
            return LocalDateDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
