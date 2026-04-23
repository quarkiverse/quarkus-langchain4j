package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Vertx;

public class ToolExecutionModelVirtualThreadDispatchTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class));

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void virtualThreadBatchRunsOnVirtualThread() {
        String uuid = UUID.randomUUID().toString();
        String r = aiService.singleVirtualTool("mem-" + uuid, "hiVirtualThread - " + uuid)
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains(uuid, "quarkus-virtual-thread-");
    }

    @Test
    void blockingBatchRunsOnWorkerThread() {
        String uuid = UUID.randomUUID().toString();
        String r = invokeFromEventLoop(() -> aiService.singleBlockingTool("mem-" + uuid, "hi - " + uuid));
        // When the streaming call is subscribed on the event loop, the tool loop is dispatched
        // onto the Quarkus worker pool (`executor-thread-<n>`) — not onto a virtual thread.
        assertThat(r).contains(uuid, "executor-thread");
        assertThat(r).doesNotContain("quarkus-virtual-thread-");
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void mixedBatchFallsBackToWorkerDispatch() {
        String uuid = UUID.randomUUID().toString();
        String r = invokeFromEventLoop(() -> aiService.mixedTools("mem-" + uuid, "hi,hiVirtualThread - " + uuid));
        // Blocking tool must run on a worker thread.
        assertThat(r).contains("BLOCKING:").contains("executor-thread");
        // Virtual-thread tool still lands on a virtual thread (QuarkusToolExecutor submits it
        // per-tool even in the mixed-batch fallback path).
        assertThat(r).contains("VIRTUAL:").contains("quarkus-virtual-thread-");
        assertThat(r).contains(uuid);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void virtualThreadToolFailureSurfacesThroughToolErrorHandler() {
        // Quarkus wires a default toolExecutionErrorHandler that converts tool exceptions into
        // tool-result text (so the LLM can recover). We assert the exception message ends up in
        // the final stream output, proving the exception thrown inside the virtual-thread
        // dispatch is captured and routed back through the normal tool-result pipeline.
        String r = aiService.failingVirtualTool("mem-" + UUID.randomUUID(), "failingVirtualThread - boom")
                .collect().asList().map(l -> String.join(" ", l)).await().indefinitely();
        assertThat(r).contains("boom");
    }

    private String invokeFromEventLoop(Supplier<Multi<String>> serviceCall) {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        vertx.getOrCreateContext().runOnContext(x -> {
            try {
                Arc.container().requestContext().activate();
                serviceCall.get()
                        .collect().asList().map(l -> String.join(" ", l))
                        .subscribeAsCompletionStage()
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                failure.set(t);
                            } else {
                                result.set(r);
                            }
                        });
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                Arc.container().requestContext().deactivate();
            }
        });
        Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> failure.get() != null || result.get() != null);
        if (failure.get() != null) {
            throw new AssertionError("Service call failed", failure.get());
        }
        return result.get();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(VirtualTool.class)
        Multi<String> singleVirtualTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(BlockingTool.class)
        Multi<String> singleBlockingTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox({ BlockingTool.class, VirtualTool.class })
        Multi<String> mixedTools(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);

        @ToolBox(FailingVirtualTool.class)
        Multi<String> failingVirtualTool(@MemoryId String memoryId, @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class BlockingTool {
        @Tool("hi")
        public String hi(String m) {
            return "BLOCKING:" + m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class VirtualTool {
        @Tool("hiVirtualThread")
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return "VIRTUAL:" + m + " " + Thread.currentThread();
        }
    }

    @Singleton
    public static class FailingVirtualTool {
        @Tool("failingVirtualThread")
        @RunOnVirtualThread
        public String failingVirtualThread(String m) {
            throw new RuntimeException("boom: " + m);
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new MultiToolChatModel();
        }
    }

    /**
     * Chat model that parses a comma-separated list of tool names from the user message
     * (format: {@code "tool1[,tool2] - content"}) and emits one {@link ToolExecutionRequest}
     * per tool in a single {@link AiMessage}. Any follow-up request containing
     * {@link ToolExecutionResultMessage}s terminates the conversation by echoing their
     * concatenated text.
     */
    public static class MultiToolChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            boolean hasToolResults = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
            if (!hasToolResults) {
                dev.langchain4j.data.message.UserMessage userMessage = null;
                for (ChatMessage message : messages) {
                    if (message instanceof dev.langchain4j.data.message.UserMessage um) {
                        userMessage = um;
                    }
                }
                if (userMessage == null) {
                    handler.onError(new RuntimeException("No user message found"));
                    return;
                }
                String text = userMessage.singleText();
                String[] segments = text.split(" - ", 2);
                if (segments.length != 2) {
                    handler.onError(new RuntimeException("Bad user message: " + text));
                    return;
                }
                String[] toolIds = segments[0].split(",");
                String content = segments[1];
                java.util.List<ToolExecutionRequest> requests = new java.util.ArrayList<>();
                int i = 0;
                for (String toolId : toolIds) {
                    requests.add(ToolExecutionRequest.builder()
                            .id("call-" + toolId + "-" + (i++))
                            .name(toolId.trim())
                            .arguments("{\"m\":\"" + content + "\"}")
                            .build());
                }
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(new AiMessage("cannot be blank", requests))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
                handler.onCompleteResponse(chatResponse);
            } else {
                StringBuilder combined = new StringBuilder();
                for (ChatMessage message : messages) {
                    if (message instanceof ToolExecutionResultMessage trm) {
                        if (combined.length() > 0) {
                            combined.append(" | ");
                        }
                        combined.append(trm.text());
                    }
                }
                handler.onPartialResponse("response: ");
                handler.onPartialResponse(combined.toString());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage(""))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };
        }
    }

}
