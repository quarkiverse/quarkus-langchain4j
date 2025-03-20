package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.internal.Json;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Copied from {@code dev.langchain4j.internal.JsonTest} to ensure that our integration behaves as expected
 */
public class JsonTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void conversionToJsonAndFromJsonWorks() {
        TestData testData = new TestData();
        testData.setSampleDate(LocalDate.of(2023, 1, 15));
        testData.setSampleDateTime(LocalDateTime.of(2023, 1, 15, 10, 20));
        testData.setSomeValue("value");

        String json = Json.toJson(testData);

        assertThat(json)
                .isEqualTo(
                        "{\n" +
                                "  \"sampleDate\" : \"2023-01-15\",\n" +
                                "  \"sampleDateTime\" : \"2023-01-15T10:20:00\",\n" +
                                "  \"some_value\" : \"value\"\n" +
                                "}");

        TestData deserializedData = Json.fromJson(json, TestData.class);

        assertThat(deserializedData.getSampleDate()).isEqualTo(testData.getSampleDate());
        assertThat(deserializedData.getSampleDateTime()).isEqualTo(testData.getSampleDateTime());
        assertThat(deserializedData.getSomeValue()).isEqualTo(testData.getSomeValue());
    }

    @Test
    void readingLocalDateFromConstituentsWorks() {
        String json = """
                {
                    "sampleDate": {
                        "year": 2023,
                        "month": 1,
                        "day": 15
                    },
                    "sampleDateTime": {
                        "date": {
                            "year": 2023,
                            "month": 1,
                            "day": 15
                        },
                        "time": {
                            "hour": 10,
                            "minute": 20,
                            "second": 0
                        }
                    },
                    "some_value": "value"
                }
                """;

        TestData deserializedData = Json.fromJson(json, TestData.class);

        assertThat(deserializedData.getSampleDate()).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(deserializedData.getSampleDateTime()).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 20));
        assertThat(deserializedData.getSomeValue()).isEqualTo("value");
    }

    @Test
    void readingLocalDateFromConstituentsWithNonsensicalDataWorks() {
        String json = """
                {
                    "sampleDate": {
                        "year": 0,
                        "month": 0,
                        "day": 0
                    },
                    "sampleDateTime": {
                        "date": {
                            "year": 2023,
                            "month": 1,
                            "day": 15
                        },
                        "time": {
                            "hour": 10,
                            "minute": 20,
                            "second": 0
                        }
                    },
                    "some_value": "value"
                }
                """;

        TestData deserializedData = Json.fromJson(json, TestData.class);

        assertThat(deserializedData.getSampleDate()).isNull();
        assertThat(deserializedData.getSampleDateTime()).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 20));
        assertThat(deserializedData.getSomeValue()).isEqualTo("value");
    }

    @Test
    void toInputStreamWorksForList() throws IOException {
        List<TestObject> testObjects = Arrays.asList(
                new TestObject("John", LocalDate.of(2021, 8, 17), LocalDateTime.of(2021, 8, 17, 14, 20)),
                new TestObject("Jane", LocalDate.of(2021, 8, 16), LocalDateTime.of(2021, 8, 16, 13, 19)));

        String expectedJson = "[ {  " +
                "\"name\" : \"John\",  " +
                "\"date\" : \"2021-08-17\",  " +
                "\"dateTime\" : \"2021-08-17T14:20:00\"" +
                "}, {  " +
                "\"name\" : \"Jane\",  " +
                "\"date\" : \"2021-08-16\",  " +
                "\"dateTime\" : \"2021-08-16T13:19:00\"" +
                "} ]";

        String resultJson = Json.toJson(testObjects);
        assertThat(resultJson).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void illegalUnquotedChar() {
        String text = """
                Here is the options:
                 - option 1
                 - option 2
                """;
        Response expectedResponse = new Response(text);

        String json = "{  " +
                "\"answer\" : \"" + text + "\"" +
                "}";
        Response actualResponse = Json.fromJson(json, Response.class);

        assertThat(actualResponse.getAnswer()).isEqualTo(expectedResponse.getAnswer());
    }

    private static class Response {
        private final String answer;

        @JsonCreator
        public Response(String answer) {
            this.answer = answer;
        }

        public String getAnswer() {
            return this.answer;
        }
    }

    private static class TestObject {
        private final String name;
        private final LocalDate date;
        private final LocalDateTime dateTime;

        @JsonCreator
        public TestObject(String name, LocalDate date, LocalDateTime dateTime) {
            this.name = name;
            this.date = date;
            this.dateTime = dateTime;
        }
    }

    private static class TestData {

        private LocalDate sampleDate;
        private LocalDateTime sampleDateTime;
        @JsonProperty("some_value")
        private String someValue;

        LocalDate getSampleDate() {
            return sampleDate;
        }

        void setSampleDate(LocalDate sampleDate) {
            this.sampleDate = sampleDate;
        }

        LocalDateTime getSampleDateTime() {
            return sampleDateTime;
        }

        void setSampleDateTime(LocalDateTime sampleDateTime) {
            this.sampleDateTime = sampleDateTime;
        }

        String getSomeValue() {
            return someValue;
        }

        void setSomeValue(String someValue) {
            this.someValue = someValue;
        }
    }
}
