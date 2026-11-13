package io.quarkiverse.langchain4j.runtime.jackson;

import java.time.DateTimeException;
import java.time.LocalTime;

import org.jboss.logging.Logger;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;

/**
 * Often LLMs return a time as a JSON object containing the time's constituents
 */
public class CustomLocalTimeDeserializer extends ValueDeserializer<LocalTime> {

    private static final Logger log = Logger.getLogger(CustomLocalTimeDeserializer.class);

    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.readValueAsTree();
            int hour = node.get("hour").asInt();
            int minute = node.get("minute").asInt();
            int second = node.get("second").asInt();
            int nano = node.get("nano").asInt();
            try {
                return LocalTime.of(hour, minute, second, nano);
            } catch (DateTimeException e) {
                log.debug("Failed to deserialize LocalTime", e);
                // in this case the LLM returned something that makes no sense (like all fields being zero), so best treat it as null
                return null;
            }
        } else {
            // delegate to Jackson's own deserializer so that a value it cannot parse fails loudly instead of
            // silently becoming null
            return LocalTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
