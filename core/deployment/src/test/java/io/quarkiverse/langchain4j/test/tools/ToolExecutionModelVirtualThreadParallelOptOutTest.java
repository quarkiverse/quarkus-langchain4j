package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
import io.quarkiverse.langchain4j.runtime.VirtualThreadSupport;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;

/**
 * Asserts that {@code quarkus.langchain4j.tools.parallel-virtual-thread-batch=false} restores
 * the historical serialized-loop behavior: every tool in a virtual-thread batch runs on the
 * single carrier virtual thread.
 */
public class ToolExecutionModelVirtualThreadParallelOptOutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class))
            .overrideConfigKey("quarkus.langchain4j.tools.parallel-virtual-thread-batch", "false");

    @Inject
    MyAiService aiService;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void serializedBatchRunsAllToolsOnTheSameVirtualThread() throws InterruptedException {
        ThreadCapturingTool.invocationThreads.clear();
        String uuid = UUID.randomUUID().toString();
        executeChatEventStreamBlocking(
                () -> aiService.threeTools("mem-" + uuid, "tA,tB,tC - " + uuid));

        assertThat(ThreadCapturingTool.invocationThreads).hasSize(3);
        assertThat(ThreadCapturingTool.invocationThreads)
                .allSatisfy(t -> assertThat(VirtualThreadSupport.isVirtualThread(t)).isTrue());
        assertThat(new HashSet<>(ThreadCapturingTool.invocationThreads))
                .as("Opt-out keeps the serialized-loop carrier — every tool runs on the same virtual thread")
                .hasSize(1);
    }

    private void executeChatEventStreamBlocking(Supplier<Multi<ChatEvent>> serviceCall) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        serviceCall.get()
                .subscribe().with(
                        item -> {
                        },
                        t -> {
                            failure.set(t);
                            latch.countDown();
                        },
                        latch::countDown);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Chat event stream failed", failure.get());
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(ThreadCapturingTool.class)
        Multi<ChatEvent> threeTools(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolIds);
    }

    @Singleton
    public static class ThreadCapturingTool {

        static final List<Thread> invocationThreads = new CopyOnWriteArrayList<>();

        @Tool("tA")
        @RunOnVirtualThread
        public String tA(String m) {
            return capture("A", m);
        }

        @Tool("tB")
        @RunOnVirtualThread
        public String tB(String m) {
            return capture("B", m);
        }

        @Tool("tC")
        @RunOnVirtualThread
        public String tC(String m) {
            return capture("C", m);
        }

        private String capture(String label, String m) {
            invocationThreads.add(Thread.currentThread());
            return label + ":" + m;
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new MultiToolChatModel();
        }
    }

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
                String[] segments = userMessage.singleText().split(" - ", 2);
                String[] toolIds = segments[0].split(",");
                String content = segments[1];
                List<ToolExecutionRequest> requests = new ArrayList<>();
                int i = 0;
                for (String toolId : toolIds) {
                    requests.add(ToolExecutionRequest.builder()
                            .id("call-" + toolId + "-" + (i++))
                            .name(toolId.trim())
                            .arguments("{\"m\":\"" + content + "\"}")
                            .build());
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("thinking", requests))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                handler.onPartialResponse("done");
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
            return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
        }
    }
}
