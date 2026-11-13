package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.internal.Json;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The string branch of the custom {@code java.time} deserializers must behave like Jackson's own deserializers:
 * accept every format Jackson accepts, and fail on values that cannot be parsed instead of silently producing
 * {@code null}.
 */
public class JsonDateStringFormatsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void plainIsoStringsWork() {
        TestData data = Json.fromJson("""
                {
                    "date": "2023-01-15",
                    "dateTime": "2023-01-15T10:20:00",
                    "time": "10:20:00"
                }
                """, TestData.class);

        assertThat(data.date).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(data.dateTime).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 20));
        assertThat(data.time).isEqualTo(LocalTime.of(10, 20));
    }

    @Test
    void localDateAcceptsDateTimeString() {
        // LLMs frequently answer a date question with a full timestamp
        TestData data = Json.fromJson("""
                {
                    "date": "2023-01-15T10:20:00"
                }
                """, TestData.class);

        assertThat(data.date).isEqualTo(LocalDate.of(2023, 1, 15));
    }

    @Test
    void localDateTimeAcceptsUtcSuffix() {
        TestData data = Json.fromJson("""
                {
                    "dateTime": "2023-01-15T10:20:00Z"
                }
                """, TestData.class);

        assertThat(data.dateTime).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 20));
    }

    @Test
    void unparseableLocalDateStringFails() {
        assertThatThrownBy(() -> Json.fromJson("""
                {
                    "date": "January 15th, 2023"
                }
                """, TestData.class)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unparseableLocalDateTimeStringFails() {
        assertThatThrownBy(() -> Json.fromJson("""
                {
                    "dateTime": "yesterday at noon"
                }
                """, TestData.class)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unparseableLocalTimeStringFails() {
        assertThatThrownBy(() -> Json.fromJson("""
                {
                    "time": "half past ten"
                }
                """, TestData.class)).isInstanceOf(RuntimeException.class);
    }

    static class TestData {
        LocalDate date;
        LocalDateTime dateTime;
        LocalTime time;
    }
}
