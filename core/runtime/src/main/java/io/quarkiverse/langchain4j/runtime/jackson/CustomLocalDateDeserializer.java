package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;

import org.jboss.logging.Logger;

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

    private static final Logger log = Logger.getLogger(CustomLocalDateDeserializer.class);

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            int year = node.get("year").asInt();
            int month = node.get("month").asInt();
            int day = node.get("day").asInt();
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                log.debug("Failed to deserialize LocalDate", e);
                // in this case the LLM returned something that makes no sense (like all fields being zero), so best treat it as null
                return null;
            }
        } else {
            return LocalDateDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
