package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.gemini.common.GeminiTokenUsage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiTokenUsageTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;
    private static final String RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"thoughtsTokenCount":20,
                              "cachedContentTokenCount":4,"totalTokenCount":35}}""";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void thinkingAndCachedTokensAreReported() {
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":generateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(RESPONSE)));

        assertUsage(chatModel.chat(UserMessage.from("hello")).tokenUsage());
    }

    @Test
    void thinkingAndCachedTokensAreReportedWhenStreaming() throws Exception {
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":streamGenerateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                        .withBody("data: " + RESPONSE.replace("\n", "") + "\n\n")));

        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });

        assertUsage(response.get(30, TimeUnit.SECONDS).tokenUsage());
    }

    private static void assertUsage(TokenUsage tokenUsage) {
        assertThat(tokenUsage).isInstanceOf(GeminiTokenUsage.class);
        GeminiTokenUsage geminiTokenUsage = (GeminiTokenUsage) tokenUsage;
        assertThat(geminiTokenUsage.inputTokenCount()).isEqualTo(10);
        assertThat(geminiTokenUsage.outputTokenCount()).isEqualTo(5);
        assertThat(geminiTokenUsage.thoughtsTokenCount()).isEqualTo(20);
        assertThat(geminiTokenUsage.cachedContentTokenCount()).isEqualTo(4);
        assertThat(geminiTokenUsage.totalTokenCount()).isEqualTo(35);
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
