package io.quarkiverse.langchain4j.openai.test;

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
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiResponsesStreamingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "openai")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void streamsText() throws Exception {
        mock.chat().reply(ChatReply.text("Hello from streamed Responses"));

        AtomicInteger partials = new AtomicInteger();
        ChatResponse response = stream(ChatRequest.builder().messages(UserMessage.from("hi")).build(), partials);

        assertThat(response.aiMessage().text()).isEqualTo("Hello from streamed Responses");
        assertThat(partials.get()).isGreaterThanOrEqualTo(1);
        assertThat(mock.chat().singleRequest().path()).endsWith("/v1/responses");
        assertThat(mock.chat().singleRequest().asChat().stream()).isTrue();
    }

    @Test
    void streamsToolCalls() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        ChatResponse response = stream(ChatRequest.builder().messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build())
                .build(), new AtomicInteger());

        assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.arguments()).contains("Paris");
        });
    }

    private ChatResponse stream(ChatRequest request, AtomicInteger partials) throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
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
        return done.get(10, TimeUnit.SECONDS);
    }
}
