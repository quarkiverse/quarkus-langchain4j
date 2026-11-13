package io.quarkiverse.langchain4j.runtime.jackson;

import java.time.DateTimeException;
import java.time.LocalDate;

import org.jboss.logging.Logger;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;

/**
 * Often LLMs return a date as a JSON object containing the date's constituents
 */
public class CustomLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    private static final Logger log = Logger.getLogger(CustomLocalDateDeserializer.class);

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.readValueAsTree();
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
            // delegate to Jackson's own deserializer so that all the formats it accepts (plain ISO dates, but also
            // date-time strings such as "2024-03-15T00:00:00" or "2024-03-15T00:00:00Z") keep working, and so that
            // a value Jackson cannot parse fails loudly instead of silently becoming null
            return LocalDateDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
