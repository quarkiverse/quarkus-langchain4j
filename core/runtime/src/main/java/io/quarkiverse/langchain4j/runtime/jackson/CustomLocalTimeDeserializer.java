package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.time.LocalTime;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;

/**
 * Often LLMs return a time as a JSON object containing the time's constituents
 */
public class CustomLocalTimeDeserializer extends JsonDeserializer<LocalTime> {

    private static final Logger log = Logger.getLogger(CustomLocalTimeDeserializer.class);

    @Override
    public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            int hour = node.get("hour").asInt();
            int minute = node.get("minute").asInt();
            int second = node.get("second").asInt();
            int nano = node.get("nano").asInt();
            try {
                return LocalTime.of(hour, minute, second, nano);
            } catch (Exception e) {
                log.debug("Failed to deserialize LocalTime", e);
                // in this case the LLM returned something that makes no sense (like all fields being zero), so best treat it as null
                return null;
            }
        } else {
            return LocalTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
