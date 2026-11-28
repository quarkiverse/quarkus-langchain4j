package io.quarkiverse.langchain4j.mistralai.deployment;

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
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The Quarkus Mistral client must report streamed tool calls through the typed
 * {@link StreamingChatResponseHandler#onPartialToolCall(PartialToolCall)} and
 * {@link StreamingChatResponseHandler#onCompleteToolCall(CompleteToolCall)} callbacks, as langchain4j's own
 * Mistral client does, and not only on the final {@link ChatResponse}.
 */
public class MistralStreamingToolCallCallbacksTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "mistralai");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void reportsEachStreamedToolCallBeforeTheCompleteResponse() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getWeather", Map.of("city", "Paris"))
                .addToolCall("getTime", Map.of("zone", "Europe/Paris")));

        List<String> events = new CopyOnWriteArrayList<>();
        List<PartialToolCall> partialToolCalls = new CopyOnWriteArrayList<>();
        List<CompleteToolCall> completeToolCalls = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();

        streamingChatModel.chat(ChatRequest.builder().messages(UserMessage.from("weather and time?"))
                .toolSpecifications(
                        ToolSpecification.builder().name("getWeather")
                                .parameters(JsonObjectSchema.builder().addStringProperty("city").build()).build(),
                        ToolSpecification.builder().name("getTime")
                                .parameters(JsonObjectSchema.builder().addStringProperty("zone").build()).build())
                .build(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        events.add("partialResponse");
                    }

                    @Override
                    public void onPartialToolCall(PartialToolCall partialToolCall) {
                        events.add("partialToolCall");
                        partialToolCalls.add(partialToolCall);
                    }

                    @Override
                    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                        events.add("completeToolCall");
                        completeToolCalls.add(completeToolCall);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        events.add("completeResponse");
                        done.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        done.completeExceptionally(error);
                    }
                });

        ChatResponse response = done.get(10, TimeUnit.SECONDS);

        // the final response is unchanged
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(2);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(response.aiMessage().toolExecutionRequests().get(1).name()).isEqualTo("getTime");

        // every tool call is reported as complete, in order, with the same request the response carries
        assertThat(completeToolCalls).hasSize(2);
        assertThat(completeToolCalls.get(0).index()).isEqualTo(0);
        assertThat(completeToolCalls.get(0).toolExecutionRequest())
                .isEqualTo(response.aiMessage().toolExecutionRequests().get(0));
        assertThat(completeToolCalls.get(1).index()).isEqualTo(1);
        assertThat(completeToolCalls.get(1).toolExecutionRequest())
                .isEqualTo(response.aiMessage().toolExecutionRequests().get(1));

        // the arguments were also streamed as partial tool calls, carrying the call's index, id and name
        assertThat(partialToolCalls).isNotEmpty();
        assertThat(partialToolCalls).filteredOn(p -> p.index() == 0).isNotEmpty().allSatisfy(p -> {
            assertThat(p.id()).isEqualTo("call_mock_0");
            assertThat(p.name()).isEqualTo("getWeather");
        });
        assertThat(partialToolCalls).filteredOn(p -> p.index() == 1).isNotEmpty().allSatisfy(p -> {
            assertThat(p.id()).isEqualTo("call_mock_1");
            assertThat(p.name()).isEqualTo("getTime");
        });
        String firstArguments = String.join("", partialToolCalls.stream().filter(p -> p.index() == 0)
                .map(PartialToolCall::partialArguments).toList());
        assertThat(firstArguments).isEqualTo("{\"city\":\"Paris\"}");
        String secondArguments = String.join("", partialToolCalls.stream().filter(p -> p.index() == 1)
                .map(PartialToolCall::partialArguments).toList());
        assertThat(secondArguments).isEqualTo("{\"zone\":\"Europe/Paris\"}");

        // and all of it arrives before the complete response, nothing after it
        assertThat(events).last().isEqualTo("completeResponse");
        assertThat(events).containsSubsequence("partialToolCall", "completeToolCall", "completeToolCall",
                "completeResponse");
        assertThat(events).doesNotContain("partialResponse");
    }

    @Test
    void reportsAToolCallWithoutArgumentsAsCompleteWithAnEmptyObject() throws Exception {
        // empty arguments on the wire, so that no argument fragment is streamed for this tool call
        mock.chat().reply(ChatReply.toolCall("getTime", ""));

        List<PartialToolCall> partialToolCalls = new CopyOnWriteArrayList<>();
        List<CompleteToolCall> completeToolCalls = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();

        streamingChatModel.chat(ChatRequest.builder().messages(UserMessage.from("time?"))
                .toolSpecifications(ToolSpecification.builder().name("getTime").build())
                .build(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialToolCall(PartialToolCall partialToolCall) {
                        partialToolCalls.add(partialToolCall);
                    }

                    @Override
                    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                        completeToolCalls.add(completeToolCall);
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
        assertThat(response.aiMessage().toolExecutionRequests()).singleElement()
                .satisfies(t -> assertThat(t.arguments()).isEqualTo("{}"));
        assertThat(completeToolCalls).singleElement().satisfies(c -> {
            assertThat(c.index()).isEqualTo(0);
            assertThat(c.toolExecutionRequest().name()).isEqualTo("getTime");
            assertThat(c.toolExecutionRequest().arguments()).isEqualTo("{}");
        });
        // a tool call is always announced by exactly one partial when it has no arguments
        assertThat(partialToolCalls).singleElement().satisfies(p -> {
            assertThat(p.index()).isEqualTo(0);
            assertThat(p.id()).isEqualTo("call_mock_0");
            assertThat(p.name()).isEqualTo("getTime");
            assertThat(p.partialArguments()).isEqualTo("{}");
        });
    }

    @Test
    void reportsAnErrorWhenTheHandlerFailsOnTheLastToolCall() throws Exception {
        mock.chat().reply(ChatReply.toolCall("getTime", Map.of("zone", "Europe/Paris")));

        CompletableFuture<Throwable> failure = new CompletableFuture<>();

        streamingChatModel.chat(ChatRequest.builder().messages(UserMessage.from("time?"))
                .toolSpecifications(ToolSpecification.builder().name("getTime")
                        .parameters(JsonObjectSchema.builder().addStringProperty("zone").build()).build())
                .build(), new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                        throw new IllegalStateException("boom");
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        failure.completeExceptionally(
                                new AssertionError("onCompleteResponse must not follow a failed tool call callback"));
                    }

                    @Override
                    public void onError(Throwable error) {
                        failure.complete(error);
                    }
                });

        // the last tool call is only complete when the stream ends, and the failure must still reach the handler
        assertThat(failure.get(10, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }
}
