package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Test that {@link ChatEvent.PartialToolCallEvent} is emitted when the model streams
 * tool call arguments via {@code onPartialToolCall}.
 */
public class ToolExecutionModelWithStreamingPartialToolCallTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, Lists.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void testPartialToolCallEventsAreEmitted() {
        List<ChatEvent> receivedEvents = new CopyOnWriteArrayList<>();

        aiService.chat("mem1", "trigger")
                .onItem().invoke(receivedEvents::add)
                .collect().asList()
                .await().indefinitely();

        // Verify PartialToolCallEvents were emitted
        List<ChatEvent.PartialToolCallEvent> partialToolCallEvents = receivedEvents.stream()
                .filter(e -> e instanceof ChatEvent.PartialToolCallEvent)
                .map(e -> (ChatEvent.PartialToolCallEvent) e)
                .toList();

        assertThat(partialToolCallEvents)
                .as("Should receive partial tool call events from streaming")
                .isNotEmpty();

        // Verify the first partial carries tool metadata
        ChatEvent.PartialToolCallEvent first = partialToolCallEvents.get(0);
        assertThat(first.getPartialToolCall().name()).isEqualTo("doSomething");
        assertThat(first.getPartialToolCall().id()).isEqualTo("call-1");
        assertThat(first.getPartialToolCall().index()).isEqualTo(0);

        // Verify partial arguments were streamed in chunks
        assertThat(partialToolCallEvents).hasSizeGreaterThanOrEqualTo(2);

        // Verify the event type enum
        assertThat(first.getEventType()).isEqualTo(ChatEvent.ChatEventType.PartialToolCall);

        // Verify the full event sequence includes intermediate, before-tool, tool-executed, and completed
        assertThat(receivedEvents.stream().map(ChatEvent::getEventType).toList())
                .contains(
                        ChatEvent.ChatEventType.PartialToolCall,
                        ChatEvent.ChatEventType.IntermediateResponse,
                        ChatEvent.ChatEventType.BeforeToolExecution,
                        ChatEvent.ChatEventType.ToolExecuted,
                        ChatEvent.ChatEventType.Completed);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(SimpleTool.class)
        Multi<ChatEvent> chat(@MemoryId String memoryId, @UserMessage String message);
    }

    @Singleton
    public static class SimpleTool {
        @Tool
        public String doSomething(String input) {
            return "result-" + input;
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new PartialToolCallChatModel();
        }
    }

    /**
     * A mock streaming chat model that emits {@code onPartialToolCall} events
     * before completing with a tool execution request, simulating how real providers
     * (Anthropic, OpenAI) stream tool call arguments token by token.
     */
    public static class PartialToolCallChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();

            if (messages.size() == 1) {
                // Round 1: stream partial tool call arguments, then complete with tool request

                // Simulate streaming tool call arguments in chunks
                handler.onPartialToolCall(
                        PartialToolCall.builder()
                                .index(0).id("call-1").name("doSomething")
                                .partialArguments("{\"in")
                                .build(),
                        new PartialToolCallContext(new StreamingHandle() {
                            @Override
                            public void cancel() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }));

                handler.onPartialToolCall(
                        PartialToolCall.builder()
                                .index(0).id("call-1").name("doSomething")
                                .partialArguments("put\":\"")
                                .build(),
                        new PartialToolCallContext(new StreamingHandle() {
                            @Override
                            public void cancel() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }));

                handler.onPartialToolCall(
                        PartialToolCall.builder()
                                .index(0).id("call-1").name("doSomething")
                                .partialArguments("hello\"}")
                                .build(),
                        new PartialToolCallContext(new StreamingHandle() {
                            @Override
                            public void cancel() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }));

                // Complete with the assembled tool execution request
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("call-1")
                                        .name("doSomething")
                                        .arguments("{\"input\":\"hello\"}")
                                        .build())))
                        .tokenUsage(new TokenUsage(10, 20))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());

            } else if (messages.size() == 3) {
                // Round 2: tool result received, return final text response
                handler.onPartialResponse("Done!");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("Done!"))
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.STOP)
                        .build());
            } else {
                handler.onError(new RuntimeException("Unexpected message count: " + messages.size()));
            }
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }
}
