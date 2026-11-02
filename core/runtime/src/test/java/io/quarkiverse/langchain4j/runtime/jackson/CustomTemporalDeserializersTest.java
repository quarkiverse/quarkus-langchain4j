package io.quarkiverse.langchain4j.runtime.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * The custom deserializers read a date or a time from the JSON object of constituents that LLMs like to answer with.
 */
class CustomTemporalDeserializersTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new SimpleModule()
                    .addDeserializer(LocalDate.class, new CustomLocalDateDeserializer())
                    .addDeserializer(LocalDateTime.class, new CustomLocalDateTimeDeserializer())
                    .addDeserializer(LocalTime.class, new CustomLocalTimeDeserializer()));

    @Test
    void readingCompleteConstituentsWorks() throws Exception {
        assertEquals(LocalDate.of(2024, 3, 5),
                mapper.readValue("{\"year\":2024,\"month\":3,\"day\":5}", LocalDate.class));
        assertEquals(LocalDateTime.of(2024, 3, 5, 14, 30),
                mapper.readValue("{\"date\":{\"year\":2024,\"month\":3,\"day\":5},\"time\":{\"hour\":14,\"minute\":30}}",
                        LocalDateTime.class));
    }

    @Test
    void readingAnIncompleteDateYieldsNull() throws Exception {
        assertNull(mapper.readValue("{\"year\":2024,\"month\":3}", LocalDate.class));
    }

    @Test
    void readingAnIncompleteDateTimeYieldsNull() throws Exception {
        // the model left the time out
        assertNull(mapper.readValue("{\"date\":{\"year\":2024,\"month\":3,\"day\":5}}", LocalDateTime.class));
    }

    @Test
    void readingATimeWithoutSecondsWorks() throws Exception {
        // a model answering to the minute leaves the seconds out
        assertEquals(LocalTime.of(14, 30), mapper.readValue("{\"hour\":14,\"minute\":30}", LocalTime.class));
    }
}
