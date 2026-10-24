package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * A streaming AI service method ({@code Multi<String>}) backed by the Responses API.
 */
public class ResponsesStreamingAiServiceTest {

    @RegisterAiService
    @ApplicationScoped
    interface Assistant {

        Multi<String> chat(String message);
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key");

    @Inject
    Assistant assistant;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void tokensAreStreamedFromResponsesApi() {
        // the mock streams a reply one word at a time, each token after the first with its leading space
        mock.chat().reply("Hallo Welt");

        List<String> tokens = assistant.chat("Translate to German: Hello world!")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(30));

        assertThat(tokens).containsExactly("Hallo", " Welt");
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        JsonNode body = request.bodyAsJson();
        assertThat(body.path("stream").booleanValue()).isTrue();
        assertThat(body.path("model").textValue()).isEqualTo("gpt-6-luna");
    }
}
