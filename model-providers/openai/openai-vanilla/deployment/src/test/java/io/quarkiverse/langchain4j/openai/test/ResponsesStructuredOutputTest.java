package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Structured output over the Responses API: the AI service's JSON schema must be sent as {@code text.format} and the
 * returned JSON must be mapped back to the return type.
 */
public class ResponsesStructuredOutputTest extends OpenAiBaseTest {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    PersonExtractor personExtractor;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
    }

    @Test
    void jsonSchemaIsSentAndResultIsMapped() throws IOException {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                          "id": "resp_1",
                                          "model": "gpt-6-luna",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "message",
                                              "role": "assistant",
                                              "content": [
                                                {
                                                  "type": "output_text",
                                                  "text": "{\\"firstName\\": \\"John\\", \\"lastName\\": \\"Doe\\", \\"birthDate\\": \\"1968-07-04\\"}"
                                                }
                                              ]
                                            }
                                          ],
                                          "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                        }
                                        """)));

        Person person = personExtractor.extractPersonFrom("John Doe was born on the 4th of July 1968.");

        assertThat(person.firstName()).isEqualTo("John");
        assertThat(person.lastName()).isEqualTo("Doe");
        assertThat(person.birthDate()).isEqualTo(LocalDate.of(1968, Month.JULY, 4));

        Map<String, Object> request = getRequestAsMap();
        assertThat(request).extractingByKey("text", as(map(String.class, Object.class)))
                .extractingByKey("format", as(map(String.class, Object.class)))
                .containsEntry("type", "json_schema")
                .containsEntry("strict", true)
                .containsEntry("name", "Person")
                .containsKey("schema");
    }
}
