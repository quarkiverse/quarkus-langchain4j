package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * A streaming AI service method ({@code Multi<String>}) backed by the Responses API.
 */
public class ResponsesStreamingAiServiceTest extends OpenAiBaseTest {

    @RegisterAiService
    @ApplicationScoped
    interface Assistant {

        Multi<String> chat(String message);
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    Assistant assistant;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
    }

    @Test
    void tokensAreStreamedFromResponsesApi() throws IOException {
        var eventStream = """
                data: {"type":"response.output_text.delta","delta":"Hallo"}

                data: {"type":"response.output_text.delta","delta":" Welt"}

                data: {"type":"response.completed","response":{"id":"resp_1","model":"gpt-6-luna","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hallo Welt"}]}],"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}}

                data: [DONE]
                """;
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        List<String> tokens = assistant.chat("Translate to German: Hello world!")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(30));

        assertThat(tokens).containsExactly("Hallo", " Welt");
        assertThat(getRequestAsMap())
                .containsEntry("stream", true)
                .containsEntry("model", "gpt-6-luna");
    }
}
