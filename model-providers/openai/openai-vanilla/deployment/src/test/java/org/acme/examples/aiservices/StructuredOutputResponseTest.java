package org.acme.examples.aiservices;

import static java.time.Month.JULY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class StructuredOutputResponseTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    record Person(String firstName, String lastName, LocalDate birthDate) {
    }

    @RegisterAiService
    @ApplicationScoped
    interface PersonExtractor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @Inject
    PersonExtractor personExtractor;

    @Test
    public void testPojo() throws IOException {
        setChatCompletionMessageContent(
                // this is supposed to be a string inside a json string hence all the escaping...
                "{\\n\\\"firstName\\\": \\\"John\\\",\\n\\\"lastName\\\": \\\"Doe\\\",\\n\\\"birthDate\\\": \\\"1968-07-04\\\"\\n}");

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person result = personExtractor.extractPersonFrom(text);

        assertThat(result.firstName).isEqualTo("John");
        assertThat(result.lastName).isEqualTo("Doe");
        assertThat(result.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        Map<String, Object> requestAsMap = getRequestAsMap();
        assertSingleRequestMessage(requestAsMap,
                "Extract information about a person from In 1968, amidst the fading echoes of Independence Day, " +
                        "a child named John arrived under the calm evening sky. This newborn, bearing the surname Doe, " +
                        "marked the start of a new journey.");
        assertThat(requestAsMap).hasEntrySatisfying("response_format", (v) -> {
            assertThat(v).asInstanceOf(map(String.class, Object.class)).satisfies(responseFormatMap -> {
                assertThat(responseFormatMap).containsEntry("type", "json_schema");
                assertThat(responseFormatMap).extracting("json_schema").satisfies(js -> {
                    assertThat(js).asInstanceOf(map(String.class, Object.class)).satisfies(jsonSchemaMap -> {
                        assertThat(jsonSchemaMap).containsEntry("name", "Person").containsKey("schema");
                    });
                });
            });
        });

    }
}
