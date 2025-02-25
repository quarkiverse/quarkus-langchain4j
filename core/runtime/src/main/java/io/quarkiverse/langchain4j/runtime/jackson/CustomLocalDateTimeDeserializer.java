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
            return LocalDateTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }
}
