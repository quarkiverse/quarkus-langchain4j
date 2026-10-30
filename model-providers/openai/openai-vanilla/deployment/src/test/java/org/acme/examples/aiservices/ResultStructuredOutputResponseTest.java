package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.time.LocalDate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A {@code Result<T>} return type must advertise the schema of {@code T}, not of the
 * {@link Result} wrapper, since the output parser unwraps {@code Result} and reads the
 * response body as {@code T}.
 */
public class ResultStructuredOutputResponseTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
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
        Result<Person> extractPersonFrom(String text);
    }

    @Inject
    PersonExtractor personExtractor;

    @Test
    public void testResultOfPojo() throws IOException {
        setChatCompletionMessageContent(
                // this is supposed to be a string inside a json string hence all the escaping...
                "{\\n\\\"firstName\\\": \\\"John\\\",\\n\\\"lastName\\\": \\\"Doe\\\",\\n\\\"birthDate\\\": \\\"1968-07-04\\\"\\n}");

        Result<Person> result = personExtractor.extractPersonFrom("some text");

        assertThat(result.content().firstName).isEqualTo("John");

        assertThat(getRequestAsMap()).extracting("response_format")
                .asInstanceOf(map(String.class, Object.class))
                .extracting("json_schema").asInstanceOf(map(String.class, Object.class))
                .satisfies(jsonSchema -> {
                    assertThat(jsonSchema).containsEntry("name", "Person");
                    assertThat(jsonSchema).extracting("schema").asInstanceOf(map(String.class, Object.class))
                            .extracting("properties").asInstanceOf(map(String.class, Object.class))
                            .containsOnlyKeys("firstName", "lastName", "birthDate");
                });
    }
}
