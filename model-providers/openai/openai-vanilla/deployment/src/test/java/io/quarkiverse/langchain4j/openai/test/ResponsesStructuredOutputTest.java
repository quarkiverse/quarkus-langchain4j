package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;

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

/**
 * Structured output over the Responses API: the AI service's JSON schema must be sent as {@code text.format} and the
 * returned JSON must be mapped back to the return type.
 */
public class ResponsesStructuredOutputTest {

    record Person(String firstName, String lastName, LocalDate birthDate) {
    }

    @RegisterAiService
    @ApplicationScoped
    interface PersonExtractor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Person.class, PersonExtractor.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key");

    @Inject
    PersonExtractor personExtractor;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void jsonSchemaIsSentAndResultIsMapped() {
        mock.chat().reply("{\"firstName\": \"John\", \"lastName\": \"Doe\", \"birthDate\": \"1968-07-04\"}");

        Person person = personExtractor.extractPersonFrom("John Doe was born on the 4th of July 1968.");

        assertThat(person.firstName()).isEqualTo("John");
        assertThat(person.lastName()).isEqualTo("Doe");
        assertThat(person.birthDate()).isEqualTo(LocalDate.of(1968, Month.JULY, 4));

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        JsonNode format = request.bodyAsJson().path("text").path("format");
        assertThat(format.path("type").textValue()).isEqualTo("json_schema");
        assertThat(format.path("strict").booleanValue()).isTrue();
        assertThat(format.path("name").textValue()).isEqualTo("Person");
        assertThat(format.has("schema")).isTrue();
    }
}
