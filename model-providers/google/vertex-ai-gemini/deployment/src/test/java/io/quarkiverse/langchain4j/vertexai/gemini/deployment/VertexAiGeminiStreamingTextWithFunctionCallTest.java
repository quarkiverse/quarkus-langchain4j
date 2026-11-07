package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiStreamingTextWithFunctionCallTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void textInChunkWithFunctionCallIsKept() throws Exception {
        wiremock().register(post(urlPathEqualTo(String.format(
                "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:streamGenerateContent", CHAT_MODEL_ID)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(
                                """
                                        data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Let me check the weather."},{"functionCall":{"name":"getWeather","args":{"city":"Paris"}},"thoughtSignature":"signature-1"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2,"totalTokenCount":3}}

                                        """)));

        List<String> partialResponses = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingChatModel.chat("What is the weather in Paris?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.add(partialResponse);
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

        AiMessage aiMessage = response.get(30, TimeUnit.SECONDS).aiMessage();
        assertThat(partialResponses).containsExactly("Let me check the weather.");
        assertThat(aiMessage.text()).isEqualTo("Let me check the weather.");
        assertThat(aiMessage.toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::id)
                .containsExactly(tuple("getWeather", "signature-1"));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
