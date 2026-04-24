package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class ToolExecutionModelVirtualThreadLegacyDispatchTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class))
            .overrideConfigKey("quarkus.langchain4j.tools.dispatch", "legacy");

    @Inject
    MyAiService aiService;

    @Inject
    Vertx vertx;

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @ActivateRequestContext
    void legacyDispatchKeepsSingleVirtualToolOnLegacyPath() throws InterruptedException {
        String uuid = UUID.randomUUID().toString();
        AtomicReference<Thread> beforeToolThread = new AtomicReference<>();

        List<ChatEvent> events = invokeChatEventStreamFromEventLoop(
                () -> aiService.singleVirtualToolEvents("mem-" + uuid, "hiVirtualThread - " + uuid),
                event -> {
                    if (event instanceof ChatEvent.BeforeToolExecutionEvent) {
                        beforeToolThread.compareAndSet(null, Thread.currentThread());
                    }
                });
        String response = extractPartialResponseText(events);

        assertThat(response).contains(uuid, "quarkus-virtual-thread-");
        assertThat(beforeToolThread.get()).isNotNull();
        assertThat(beforeToolThread.get().getName()).contains("executor-thread");
        assertThat(VirtualThreadSupport.isVirtualThread(beforeToolThread.get())).isFalse();
    }

    private List<ChatEvent> invokeChatEventStreamFromEventLoop(Supplier<Multi<ChatEvent>> serviceCall,
            Consumer<ChatEvent> eventObserver) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<ChatEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Context context = vertx.getOrCreateContext();

        context.runOnContext(x -> {
            Arc.container().requestContext().activate();
            try {
                serviceCall.get()
                        .onItem().invoke(event -> {
                            events.add(event);
                            if (eventObserver != null) {
                                eventObserver.accept(event);
                            }
                        })
                        .subscribe().with(
                                item -> {
                                },
                                t -> {
                                    failure.set(t);
                                    context.runOnContext(ignored -> {
                                        Arc.container().requestContext().deactivate();
                                        latch.countDown();
                                    });
                                },
                                () -> context.runOnContext(ignored -> {
                                    Arc.container().requestContext().deactivate();
                                    latch.countDown();
                                }));
            } catch (Throwable t) {
                failure.set(t);
                Arc.container().requestContext().deactivate();
                latch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Chat event stream failed", failure.get());
        }
        return events;
    }

    private String extractPartialResponseText(List<ChatEvent> events) {
        StringBuilder response = new StringBuilder();
        for (ChatEvent event : events) {
            if (event instanceof ChatEvent.PartialResponseEvent partialResponseEvent) {
                response.append(partialResponseEvent.getChunk());
            }
        }
        return response.toString();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(VirtualTool.class)
        Multi<ChatEvent> singleVirtualToolEvents(@MemoryId String memoryId,
                @UserMessage String userMessageContainingTheToolId);
    }

    @Singleton
    public static class VirtualTool {
        @Tool("hiVirtualThread")
        @RunOnVirtualThread
        public String hiVirtualThread(String m) {
            return "VIRTUAL:" + m + " " + Thread.currentThread();
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new SingleToolChatModel();
        }
    }

    private static class SingleToolChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            boolean hasToolResults = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
            if (!hasToolResults) {
                dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage) messages
                        .get(messages.size() - 1);
                String[] segments = userMessage.singleText().split(" - ", 2);
                String toolId = segments[0];
                String content = segments[1];
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(ToolExecutionRequest.builder()
                                .id("call-" + toolId)
                                .name(toolId)
                                .arguments("{\"m\":\"" + content + "\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
                handler.onCompleteResponse(response);
            } else {
                ToolExecutionResultMessage last = null;
                for (ChatMessage message : messages) {
                    if (message instanceof ToolExecutionResultMessage trm) {
                        last = trm;
                    }
                }
                handler.onPartialResponse("response: ");
                handler.onPartialResponse(last != null ? last.text() : "");
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
