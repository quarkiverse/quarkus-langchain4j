package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiStreamingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ai-gemini");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void streamsTextInChunks() throws Exception {
        mock.chat().reply(ChatReply.text("As an AI model").usage(3, 5));

        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        AtomicInteger partials = new AtomicInteger();
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partials.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                done.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        ChatResponse response = done.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("As an AI model");
        assertThat(partials.get()).isEqualTo(4);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(3);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(5);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ai-gemini/v1beta/models/gemini-2.5-flash:streamGenerateContent");
        assertThat(r.query()).isEqualTo("alt=sse");
        assertThat(r.asChat().stream()).isTrue();
    }

    @Test
    void streamsFunctionCall() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat(ChatRequest.builder().messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build())
                .build(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        done.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        done.completeExceptionally(error);
                    }
                });

        ChatResponse response = done.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.arguments()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(mock.chat().singleRequest().asChat().tools()).singleElement()
                .satisfies(t -> assertThat(t.name()).isEqualTo("getWeather"));
    }
}
