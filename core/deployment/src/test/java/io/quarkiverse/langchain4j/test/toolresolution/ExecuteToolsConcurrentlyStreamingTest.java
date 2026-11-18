package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

/**
 * Verify that the streaming AI service methods honour {@link RegisterAiService#executeToolsConcurrently()}
 */
public class ExecuteToolsConcurrentlyStreamingTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, RequestData.class, StreamingModelSupplier.class,
                            ToolCallingStreamingModel.class));

    static volatile List<String> toolsToCall;
    static volatile List<ChatMessage> lastMessages;
    static volatile boolean reportToolCalls;

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new ToolCallingStreamingModel();
        }
    }

    public static class ToolCallingStreamingModel implements StreamingChatModel {
        @Override
        public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            ChatResponse response;
            try {
                response = respond(chatRequest, handler);
            } catch (RuntimeException e) {
                handler.onError(e);
                return;
            }
            handler.onCompleteResponse(response);
        }

        private static ChatResponse respond(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                lastMessages = messages;
                handler.onPartialResponse("done");
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(new TokenUsage(1, 1))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            List<ToolExecutionRequest> requests = new ArrayList<>();
            for (String name : toolsToCall) {
                requests.add(ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build());
            }
            if (reportToolCalls) {
                for (int i = 0; i < requests.size(); i++) {
                    handler.onCompleteToolCall(new CompleteToolCall(i, requests.get(i)));
                }
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(requests))
                    .tokenUsage(new TokenUsage(1, 1))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }

    @RequestScoped
    public static class RequestData {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @ApplicationScoped
    public static class Tools {

        static volatile CountDownLatch latch;
        static final AtomicInteger invocations = new AtomicInteger();
        static final RuntimeException BOOM = new IllegalStateException("boom");

        @Inject
        RequestData requestData;

        @Tool
        public String first() throws InterruptedException {
            return awaitOther("first");
        }

        @Tool
        public String second() throws InterruptedException {
            return awaitOther("second");
        }

        @Tool
        public String failing() throws InterruptedException {
            awaitOther("failing");
            throw BOOM;
        }

        private String awaitOther(String name) throws InterruptedException {
            invocations.incrementAndGet();
            latch.countDown();
            boolean concurrent = latch.await(500, TimeUnit.MILLISECONDS);
            return name + ":" + (concurrent ? "concurrent" : "sequential") + ":" + requestData.getValue();
        }
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentAssistant {
        Multi<String> chat(String message);
    }

    @RegisterAiService(tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface SequentialAssistant {
        Multi<String> chat(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, maxToolCallsPerResponse = 1, tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface LimitedAssistant {
        Multi<String> chat(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentEventsAssistant {
        Multi<ChatEvent> chat(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface ConcurrentTokenStreamAssistant {
        TokenStream chat(String message);
    }

    @RegisterAiService(tools = Tools.class, streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface SequentialTokenStreamAssistant {
        TokenStream chat(String message);
    }

    @Inject
    ConcurrentAssistant concurrentAssistant;

    @Inject
    SequentialAssistant sequentialAssistant;

    @Inject
    LimitedAssistant limitedAssistant;

    @Inject
    ConcurrentEventsAssistant concurrentEventsAssistant;

    @Inject
    ConcurrentTokenStreamAssistant concurrentTokenStreamAssistant;

    @Inject
    SequentialTokenStreamAssistant sequentialTokenStreamAssistant;

    @Inject
    RequestData requestData;

    @BeforeEach
    void reset() {
        Tools.latch = new CountDownLatch(2);
        Tools.invocations.set(0);
        toolsToCall = null;
        lastMessages = null;
        reportToolCalls = false;
    }

    @Test
    @ActivateRequestContext
    void streamingToolsRunConcurrentlyWithPropagatedContextAndOrderedResults() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(String.join("", collect(concurrentAssistant.chat("hello")))).isEqualTo("done");

        assertThat(toolResultIds()).containsExactly("first", "second");
        assertThat(toolResultTexts()).containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void streamingToolsRunSequentiallyByDefault() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        collect(sequentialAssistant.chat("hello"));

        assertThat(toolResultIds()).containsExactly("first", "second");
        assertThat(toolResultTexts()).containsExactly("first:sequential:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void streamingToolsWithinLimitAreExecutedBeforeLimitIsReported() {
        Tools.latch = new CountDownLatch(1);
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        Throwable thrown = catchThrowable(new ThrowingCallable() {
            @Override
            public void call() {
                collect(limitedAssistant.chat("hello"));
            }
        });

        assertThat(thrown).isInstanceOf(ToolCallsLimitExceededException.class);
        assertThat(Tools.invocations).hasValue(1);
    }

    @Test
    @ActivateRequestContext
    void failingStreamingToolDoesNotPreventOtherToolResults() {
        toolsToCall = List.of("failing", "second");
        requestData.setValue("propagated");

        collect(concurrentAssistant.chat("hello"));

        assertThat(toolResultTexts()).containsExactly("boom", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void chatEventsOfConcurrentToolsAreEmittedWithoutConcurrentSignals() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        List<ChatEvent> events = concurrentEventsAssistant.chat("hello").collect().asList().await().indefinitely();

        List<String> executedTools = new ArrayList<>();
        List<String> executionResults = new ArrayList<>();
        int beforeToolExecutionEvents = 0;
        int completedEvents = 0;
        for (ChatEvent event : events) {
            if (event instanceof ChatEvent.BeforeToolExecutionEvent) {
                beforeToolExecutionEvents++;
            } else if (event instanceof ChatEvent.ToolExecutedEvent toolExecuted) {
                ToolExecution execution = toolExecuted.getExecution();
                executedTools.add(execution.request().name());
                executionResults.add(execution.result());
            } else if (event instanceof ChatEvent.ChatCompletedEvent) {
                completedEvents++;
            }
        }

        assertThat(beforeToolExecutionEvents).isEqualTo(2);
        assertThat(executedTools).containsExactlyInAnyOrder("first", "second");
        assertThat(executionResults).containsExactlyInAnyOrder("first:concurrent:propagated", "second:concurrent:propagated");
        assertThat(completedEvents).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void tokenStreamToolsRunConcurrentlyWhenModelReportsToolCalls() throws Exception {
        reportToolCalls = true;
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(awaitTokenStream(concurrentTokenStreamAssistant.chat("hello"))).isEqualTo("done");

        assertThat(toolResultIds()).containsExactly("first", "second");
        assertThat(toolResultTexts()).containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void tokenStreamToolsRunConcurrentlyWhenModelDoesNotReportToolCalls() throws Exception {
        reportToolCalls = false;
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(awaitTokenStream(concurrentTokenStreamAssistant.chat("hello"))).isEqualTo("done");

        assertThat(toolResultIds()).containsExactly("first", "second");
        assertThat(toolResultTexts()).containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void tokenStreamToolsRunSequentiallyByDefault() throws Exception {
        reportToolCalls = false;
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(awaitTokenStream(sequentialTokenStreamAssistant.chat("hello"))).isEqualTo("done");

        assertThat(toolResultIds()).containsExactly("first", "second");
        assertThat(toolResultTexts()).containsExactly("first:sequential:propagated", "second:concurrent:propagated");
    }

    private static List<String> collect(Multi<String> chunks) {
        return chunks.collect().asList().await().indefinitely();
    }

    private static String awaitTokenStream(TokenStream tokenStream) throws Exception {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        tokenStream.onCompleteResponse(new Consumer<ChatResponse>() {
            @Override
            public void accept(ChatResponse response) {
                outcome.complete(response.aiMessage().text());
            }
        }).onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable error) {
                outcome.completeExceptionally(error);
            }
        }).start();
        return outcome.get(10, TimeUnit.SECONDS);
    }

    private static List<ToolExecutionResultMessage> toolResults() {
        List<ToolExecutionResultMessage> results = new ArrayList<>();
        for (ChatMessage message : lastMessages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                results.add(toolResult);
            }
        }
        return results;
    }

    private static List<String> toolResultIds() {
        List<String> ids = new ArrayList<>();
        for (ToolExecutionResultMessage result : toolResults()) {
            ids.add(result.id());
        }
        return ids;
    }

    private static List<String> toolResultTexts() {
        List<String> texts = new ArrayList<>();
        for (ToolExecutionResultMessage result : toolResults()) {
            texts.add(result.text());
        }
        return texts;
    }
}
