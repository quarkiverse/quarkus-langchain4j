package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.util.List;
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

/**
 * List/Set AI service return types must produce a real JSON schema (not empty {}), see #943.
 */
public class CollectionStructuredOutputResponseTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.response-format", "json_schema")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.strict-json-schema", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    record FileInfo(String filename, String content) {
    }

    @RegisterAiService
    @ApplicationScoped
    interface FileInfoExtractor {

        @UserMessage("Find data structure mentions in {{it}}")
        List<FileInfo> generate(String data);
    }

    @Inject
    FileInfoExtractor fileInfoExtractor;

    @Test
    public void testListOfPojo() throws IOException {
        setChatCompletionMessageContent(
                "{\\n\\\"values\\\": [{\\n\\\"filename\\\": \\\"a.txt\\\",\\n\\\"content\\\": \\\"hello\\\"\\n}]\\n}");

        List<FileInfo> result = fileInfoExtractor.generate("some input");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).filename()).isEqualTo("a.txt");
        assertThat(result.get(0).content()).isEqualTo("hello");

        Map<String, Object> requestAsMap = getRequestAsMap();
        assertThat(requestAsMap).hasEntrySatisfying("response_format", (v) -> {
            assertThat(v).asInstanceOf(map(String.class, Object.class)).satisfies(responseFormatMap -> {
                assertThat(responseFormatMap).containsEntry("type", "json_schema");
                assertThat(responseFormatMap).extracting("json_schema").satisfies(js -> {
                    assertThat(js).asInstanceOf(map(String.class, Object.class)).satisfies(jsonSchemaMap -> {
                        assertThat(jsonSchemaMap).containsEntry("name", "List_of_FileInfo");
                        assertThat(jsonSchemaMap).extracting("schema").asInstanceOf(map(String.class, Object.class))
                                .satisfies(schema -> {
                                    assertThat(schema).containsKey("properties");
                                    assertThat(schema.get("properties")).asInstanceOf(map(String.class, Object.class))
                                            .containsKey("values");
                                    assertThat(schema.get("properties")).asInstanceOf(map(String.class, Object.class))
                                            .extracting("values").asInstanceOf(map(String.class, Object.class))
                                            .containsEntry("type", "array");
                                });
                    });
                });
            });
        });
    }
}
