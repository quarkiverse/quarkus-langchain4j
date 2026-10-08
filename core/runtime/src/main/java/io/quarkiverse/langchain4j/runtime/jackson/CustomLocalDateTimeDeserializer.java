package io.quarkiverse.langchain4j.runtime.jackson;

import java.time.DateTimeException;
import java.time.LocalDateTime;

import org.jboss.logging.Logger;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Often LLMs return a datetime as a JSON object containing the datetime's constituents
 */
public class CustomLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    private static final Logger log = Logger.getLogger(CustomLocalDateTimeDeserializer.class);

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.readValueAsTree();
            JsonNode date = node.get("date");
            int year = date.get("year").asInt();
            int month = date.get("month").asInt();
            int day = date.get("day").asInt();
            JsonNode time = node.get("time");
            int hour = time.get("hour").asInt();
            int minute = time.get("minute").asInt();
            JsonNode secondNode = time.get("second");
            int second = 0;
            if (secondNode != null) {
                second = secondNode.asInt();
            }
            JsonNode nanoNode = time.get("nano");
            int nano = 0;
            if (nanoNode != null) {
                nano = nanoNode.asInt();
            }
            try {
                return LocalDateTime.of(year, month, day, hour, minute, second, nano);
            } catch (DateTimeException e) {
                log.debug("Failed to deserialize LocalDateTime", e);
                // in this case the LLM returned something that makes no sense (like all fields being zero), so best treat it as null
                return null;
            }
        } else {
            // Standard string format (e.g., "2024-03-15T14:30:00")
            String text = p.getString();
            if (text == null || text.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeException e) {
                log.debug("Failed to parse LocalDateTime from string: " + text, e);
                return null;
            }
        }
    }
}
