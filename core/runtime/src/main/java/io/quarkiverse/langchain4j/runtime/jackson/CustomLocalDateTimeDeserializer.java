package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDateTime;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

/**
 * Often LLMs return a datetime as a JSON object containing the datetime's constituents
 */
public class CustomLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final Logger log = Logger.getLogger(CustomLocalDateTimeDeserializer.class);

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            JsonNode date = node.get("date");
            JsonNode time = node.get("time");
            if (date == null || !date.hasNonNull("year") || !date.hasNonNull("month") || !date.hasNonNull("day")
                    || time == null || !time.hasNonNull("hour") || !time.hasNonNull("minute")) {
                // the LLM truncated its answer, so there is no datetime to build. Filling the missing parts with
                // zeroes would silently produce a wrong datetime, which is worse than admitting we have none
                log.debug("Failed to deserialize LocalDateTime: missing constituents in " + node);
                return null;
            }
            int year = date.get("year").asInt();
            int month = date.get("month").asInt();
            int day = date.get("day").asInt();
            int hour = time.get("hour").asInt();
            int minute = time.get("minute").asInt();
            // the seconds are optional, an LLM that answers to the minute leaves them out
            int second = time.hasNonNull("second") ? time.get("second").asInt() : 0;
            int nano = time.hasNonNull("nano") ? time.get("nano").asInt() : 0;
            try {
                return LocalDateTime.of(year, month, day, hour, minute, second, nano);
            } catch (DateTimeException e) {
                log.debug("Failed to deserialize LocalDateTime", e);
                // in this case the LLM returned something that makes no sense (like all fields being zero), so best treat it as null
                return null;
            }
        } else {
            return LocalDateTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
