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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class AzureOpenAiChatModelMaxCompletionTokensAndStopTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.max-completion-tokens", "500")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.stop", "END,STOP");

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
    void configuredValuesAreSent() {
        chatModel.chat("hello");

        JsonNode requestBody = mock.chat().singleRequest().bodyAsJson();
        assertThat(requestBody.get("max_completion_tokens").intValue()).isEqualTo(500);
        assertThat(requestBody.has("max_tokens")).isFalse();
        assertThat(requestBody.get("stop").toString()).isEqualTo("[\"END\",\"STOP\"]");
    }

    @Test
    void stopSequencesOfTheRequestReplaceTheConfiguredOnes() {
        chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder().stopSequences("###").build())
                .build());

        assertThat(mock.chat().singleRequest().bodyAsJson().get("stop").toString()).isEqualTo("[\"###\"]");
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
        assertThat(requestBody.get("max_completion_tokens").intValue()).isEqualTo(500);
        assertThat(requestBody.has("max_tokens")).isFalse();
        assertThat(requestBody.get("stop").toString()).isEqualTo("[\"END\",\"STOP\"]");
    }
}
