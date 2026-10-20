package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * List/Set AI service return types must produce a real JSON schema (not empty {}), see #943.
 */
public class CollectionStructuredOutputResponseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");

    record FileInfo(String filename, String content) {
    }

    @RegisterAiService
    @ApplicationScoped
    interface FileInfoExtractor {

        @UserMessage("Find data structure mentions in {{it}}")
        List<FileInfo> generate(String data);
    }

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Inject
    FileInfoExtractor fileInfoExtractor;

    @Test
    public void testListOfPojo() {
        mock.chat().reply("{\n\"values\": [{\n\"filename\": \"a.txt\",\n\"content\": \"hello\"\n}]\n}");

        List<FileInfo> result = fileInfoExtractor.generate("some input");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).filename()).isEqualTo("a.txt");
        assertThat(result.get(0).content()).isEqualTo("hello");

        JsonNode responseFormat = mock.chat().singleRequest().bodyAsJson().get("response_format");
        assertThat(responseFormat.path("type").textValue()).isEqualTo("json_schema");
        JsonNode jsonSchema = responseFormat.get("json_schema");
        assertThat(jsonSchema.path("name").textValue()).isEqualTo("List_of_FileInfo");
        JsonNode properties = jsonSchema.get("schema").get("properties");
        assertThat(properties.has("values")).isTrue();
        assertThat(properties.get("values").path("type").textValue()).isEqualTo("array");
    }
}
