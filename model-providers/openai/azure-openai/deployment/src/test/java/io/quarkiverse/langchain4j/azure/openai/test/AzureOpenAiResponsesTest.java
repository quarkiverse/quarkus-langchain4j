package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

/**
 * With {@code chat-model.mode=responses}, the chat models call the Responses API on the v1 endpoint, with the API key in
 * Azure's {@code api-key} header and the deployment name as the model.
 */
public class AzureOpenAiResponsesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint-style", "v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.temperature", "0.3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.max-tokens", "42")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.responses.store", "false");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void chatModelsUseTheResponsesApi() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(OpenAiResponsesChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(OpenAiResponsesStreamingChatModel.class);
    }

    @Test
    void chat() {
        mock.chat().reply("Hello there");

        assertThat(chatModel.chat("hello")).isEqualTo("Hello there");

        assertResponsesRequest(mock.chat().singleRequest());
    }

    @Test
    void streamingChat() throws InterruptedException {
        mock.chat().reply(ChatReply.text("Hallo Welt").chunks(List.of("Hallo", " Welt")).usage(1, 2));

        var partialTokens = new StringBuilder();
        var completeResponse = new AtomicReference<ChatResponse>();
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("Translate to German: Hello world!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                partialTokens.append(token);
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completeResponse.set(response);
                latch.countDown();
            }
        });
        if (!latch.await(1, TimeUnit.MINUTES)) {
            fail("Streaming did not complete in time");
        }

        assertThat(error.get()).isNull();
        assertThat(partialTokens.toString()).isEqualTo("Hallo Welt");
        assertThat(completeResponse.get().aiMessage().text()).isEqualTo("Hallo Welt");
        assertResponsesRequest(mock.chat().singleRequest());
    }

    private static void assertResponsesRequest(CapturedRequest request) {
        assertThat(request.path()).endsWith("/responses");
        assertThat(request.query()).isNull();
        assertThat(request.header("api-key")).isEqualTo("my-key");
        assertThat(request.header("Authorization")).isNull();

        JsonNode body = request.bodyAsJson();
        assertThat(body.path("model").asText()).isEqualTo("mock-deployment");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        // chat-model.max-tokens is used when responses.max-output-tokens is not set
        assertThat(body.path("max_output_tokens").asInt()).isEqualTo(42);
        assertThat(body.path("store").asBoolean(true)).isFalse();
    }
}
