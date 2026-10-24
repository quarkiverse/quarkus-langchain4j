package org.acme.examples.aiservices;

import static java.time.Month.JULY;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class StructuredOutputResponseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(OpenAiRequestAssertions.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");

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

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    public void testPojo() {
        mock.chat().reply("{\n\"firstName\": \"John\",\n\"lastName\": \"Doe\",\n\"birthDate\": \"1968-07-04\"\n}");

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person result = personExtractor.extractPersonFrom(text);

        assertThat(result.firstName).isEqualTo("John");
        assertThat(result.lastName).isEqualTo("Doe");
        assertThat(result.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        JsonNode requestAsJson = request.bodyAsJson();
        assertSingleRequestMessage(requestAsJson,
                "Extract information about a person from In 1968, amidst the fading echoes of Independence Day, " +
                        "a child named John arrived under the calm evening sky. This newborn, bearing the surname Doe, " +
                        "marked the start of a new journey.");
        JsonNode responseFormat = requestAsJson.get("response_format");
        assertThat(responseFormat.path("type").textValue()).isEqualTo("json_schema");
        JsonNode jsonSchema = responseFormat.get("json_schema");
        assertThat(jsonSchema.path("name").textValue()).isEqualTo("Person");
        assertThat(jsonSchema.has("schema")).isTrue();

    }
}
