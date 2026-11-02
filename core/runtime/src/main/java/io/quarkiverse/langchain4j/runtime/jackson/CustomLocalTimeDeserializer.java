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
            if (!node.hasNonNull("hour") || !node.hasNonNull("minute")) {
                // the LLM truncated its answer, so there is no time to build. Filling the missing parts with zeroes
                // would silently produce a wrong time, which is worse than admitting we have none
                log.debug("Failed to deserialize LocalTime: missing constituents in " + node);
                return null;
            }
            int hour = node.get("hour").asInt();
            int minute = node.get("minute").asInt();
            // the seconds are optional, an LLM that answers to the minute leaves them out
            int second = node.hasNonNull("second") ? node.get("second").asInt() : 0;
            int nano = node.hasNonNull("nano") ? node.get("nano").asInt() : 0;
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
