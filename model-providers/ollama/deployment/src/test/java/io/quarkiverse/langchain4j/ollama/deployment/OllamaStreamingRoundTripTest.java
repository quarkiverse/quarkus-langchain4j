package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaStreamingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ollama");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void streamsNdjsonTextInChunks() throws Exception {
        mock.chat().reply(ChatReply.text("As an AI model").usage(3, 5));

        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        List<String> partials = new CopyOnWriteArrayList<>();
        streamingChatModel.chat("hi", handler(done, partials));

        ChatResponse response = done.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("As an AI model");
        assertThat(partials).containsExactly("As", " an", " AI", " model");
        // DIVERGENCE: the last object carries the documented prompt_eval_count and eval_count, but the Quarkus streaming
        // client reads stream elements without its snake_case mapper (it looks for promptEvalCount and evalCount), so
        // no token usage reaches the caller, as with a real Ollama
        assertThat(response.tokenUsage()).isNull();
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ollama/api/chat");
        assertThat(r.asChat().stream()).isTrue();
    }

    @Test
    void streamsToolCall() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")));

        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat(ChatRequest.builder().messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build())
                .build(), handler(done, new CopyOnWriteArrayList<>()));

        ChatResponse response = done.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.arguments()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(mock.chat().singleRequest().asChat().tools()).singleElement()
                .satisfies(t -> assertThat(t.name()).isEqualTo("getWeather"));
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> done, List<String> partials) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partials.add(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                done.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        };
    }
}
