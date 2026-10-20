package io.quarkiverse.langchain4j.bedrock.deployment;

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
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The converse-stream response goes through the real AWS SDK event stream decoder, which checks both CRCs of every
 * message.
 */
public class BedrockStreamingRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "bedrock");

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

        List<String> partials = new CopyOnWriteArrayList<>();
        ChatResponse response = chat(ChatRequest.builder().messages(UserMessage.from("hi")).build(), partials);

        assertThat(response.aiMessage().text()).isEqualTo("As an AI model");
        assertThat(partials).containsExactly("As", " an", " AI", " model");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(3);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(5);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/bedrock/model/global.amazon.nova-2-lite-v1%3A0/converse-stream");
        assertThat(r.asChat().stream()).isTrue();
        assertThat(r.asChat().messages()).singleElement().satisfies(m -> assertThat(m.text()).isEqualTo("hi"));
    }

    @Test
    void streamsToolUse() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris")).usage(4, 6));

        ChatResponse response = chat(ChatRequest.builder().messages(UserMessage.from("weather?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather")
                        .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build())
                .build(), new CopyOnWriteArrayList<>());

        assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo("tooluse_mock_0");
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.arguments()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(10);
        assertThat(mock.chat().singleRequest().asChat().tools()).singleElement()
                .satisfies(t -> assertThat(t.name()).isEqualTo("getWeather"));
    }

    private ChatResponse chat(ChatRequest request, List<String> partials) throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
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
        });
        return done.get(10, TimeUnit.SECONDS);
    }
}
