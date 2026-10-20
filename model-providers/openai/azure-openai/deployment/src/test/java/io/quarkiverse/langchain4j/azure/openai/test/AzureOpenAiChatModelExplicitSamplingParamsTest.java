package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiChatModelExplicitSamplingParamsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.temperature", "0.3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.top-p", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.reasoning-effort", "medium");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
        mock.chat().reply("ok");
    }

    @Test
    void explicitlyConfiguredValuesAreSent() {
        chatModel.chat("hello");

        assertSamplingParams(mock.chat().singleRequest().bodyAsJson());
    }

    @Test
    void streamingModelAlsoSendsThem() throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                done.complete(response);
            }
        });
        assertThat(done.get(1, TimeUnit.MINUTES).aiMessage().text()).isEqualTo("ok");

        JsonNode requestBody = mock.chat().singleRequest().bodyAsJson();
        assertThat(requestBody.get("stream").booleanValue()).isTrue();
        assertSamplingParams(requestBody);
    }

    private static void assertSamplingParams(JsonNode requestBody) {
        assertThat(requestBody.get("temperature").doubleValue()).isEqualTo(0.3);
        assertThat(requestBody.get("top_p").doubleValue()).isEqualTo(0.8);
        assertThat(requestBody.get("reasoning_effort").textValue()).isEqualTo("medium");
    }
}
