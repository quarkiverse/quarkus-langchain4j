package io.quarkiverse.langchain4j.azure.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiStreamingChatModelTest extends OpenAiBaseTest {

    private static final int WIREMOCK_PORT = 8089;
    private static WireMockServer wireMockServer;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint",
                    "http://localhost:%d".formatted(WIREMOCK_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.log-responses", "true");

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(AzureOpenAiStreamingChatModel.class);

        // generated from a true API call to Azure OpenAI
        var eventStream = """
                data: {"choices":[],"created":0,"id":"","model":"","object":"","prompt_filter_results":[{"prompt_index":0,"content_filter_results":{"hate":{"filtered":false,"severity":"safe"},"self_harm":{"filtered":false,"severity":"safe"},"sexual":{"filtered":false,"severity":"safe"},"violence":{"filtered":false,"severity":"safe"}}}]}

                data: {"choices":[{"content_filter_results":{},"delta":{"content":"","refusal":null,"role":"assistant"},"finish_reason":null,"index":0,"logprobs":null}],"created":1750697159,"id":"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j","model":"gpt-4o-2024-08-06","object":"chat.completion.chunk","system_fingerprint":"fp_ee1d74bde0"}

                data: {"choices":[{"content_filter_results":{"hate":{"filtered":false,"severity":"safe"},"self_harm":{"filtered":false,"severity":"safe"},"sexual":{"filtered":false,"severity":"safe"},"violence":{"filtered":false,"severity":"safe"}},"delta":{"content":"Hallo"},"finish_reason":null,"index":0,"logprobs":null}],"created":1750697159,"id":"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j","model":"gpt-4o-2024-08-06","object":"chat.completion.chunk","system_fingerprint":"fp_ee1d74bde0"}

                data: {"choices":[{"content_filter_results":{"hate":{"filtered":false,"severity":"safe"},"self_harm":{"filtered":false,"severity":"safe"},"sexual":{"filtered":false,"severity":"safe"},"violence":{"filtered":false,"severity":"safe"}},"delta":{"content":" Welt"},"finish_reason":null,"index":0,"logprobs":null}],"created":1750697159,"id":"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j","model":"gpt-4o-2024-08-06","object":"chat.completion.chunk","system_fingerprint":"fp_ee1d74bde0"}

                data: {"choices":[{"content_filter_results":{"hate":{"filtered":false,"severity":"safe"},"self_harm":{"filtered":false,"severity":"safe"},"sexual":{"filtered":false,"severity":"safe"},"violence":{"filtered":false,"severity":"safe"}},"delta":{"content":"!"},"finish_reason":null,"index":0,"logprobs":null}],"created":1750697159,"id":"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j","model":"gpt-4o-2024-08-06","object":"chat.completion.chunk","system_fingerprint":"fp_ee1d74bde0"}

                data: {"choices":[{"content_filter_results":{},"delta":{},"finish_reason":"stop","index":0,"logprobs":null}],"created":1750697159,"id":"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j","model":"gpt-4o-2024-08-06","object":"chat.completion.chunk","system_fingerprint":"fp_ee1d74bde0"}

                data: [DONE]
                """;

        wireMockServer.stubFor(
                post(urlMatching("/chat/completions.*"))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));
        var streamingResponse = new AtomicReference<AiMessage>();
        streamingChatModel.chat("Translate to German: Hello world!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                streamingResponse.set(response.aiMessage());
            }
        });

        await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().text())
                .isNotNull()
                .isEqualTo("Hallo Welt!");

    }
}
