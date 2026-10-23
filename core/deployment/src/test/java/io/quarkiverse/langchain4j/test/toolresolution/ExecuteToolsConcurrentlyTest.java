package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.CreatedAware;
import io.quarkiverse.langchain4j.HandleToolExecutionError;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verify functionality of {@link RegisterAiService#executeToolsConcurrently()}
 */
public class ExecuteToolsConcurrentlyTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, RequestData.class, ModelSupplier.class, EventCollector.class));

    static volatile List<String> toolsToCall;
    static volatile List<ChatMessage> lastMessages;

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    List<ChatMessage> messages = chatRequest.messages();
                    if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                        lastMessages = messages;
                        return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
                    }
                    List<ToolExecutionRequest> requests = toolsToCall.stream()
                            .map(name -> ToolExecutionRequest.builder().id(name).name(name).arguments("{}").build())
                            .toList();
                    return ChatResponse.builder().aiMessage(AiMessage.from(requests)).build();
                }
            };
        }
    }

    @RequestScoped
    public static class RequestData {
        private String value;

        // accessed via methods, as a field access on the client proxy would never reach the contextual instance
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    // no client proxy, so that the field can be read directly
    @Singleton
    public static class EventCollector {
        final List<ToolExecutedEvent> toolExecutedEvents = new ArrayList<>();

        void onToolExecuted(@Observes ToolExecutedEvent event) {
            toolExecutedEvents.add(event);
        }
    }

    @ApplicationScoped
    public static class Tools {

        static volatile CountDownLatch latch;
        static final AtomicInteger invocations = new AtomicInteger();
        static final RuntimeException RETHROWN_EXCEPTION = new IllegalStateException("rethrown");
        static final IOException CHECKED_EXCEPTION = new IOException("checked");
        static volatile boolean slowFinished;

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
            throw new IllegalStateException("boom");
        }

        @Tool
        public String rethrown() throws InterruptedException {
            awaitOther("rethrown");
            throw RETHROWN_EXCEPTION;
        }

        @Tool
        public String checked() throws InterruptedException, IOException {
            awaitOther("checked");
            throw CHECKED_EXCEPTION;
        }

        @Tool
        public String failingImmediately() {
            throw RETHROWN_EXCEPTION;
        }

        @Tool
        public String slow() throws InterruptedException {
            Thread.sleep(200);
            slowFinished = true;
            return "slow";
        }

        @Tool
        public String currentThread() {
            return Thread.currentThread().getName();
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String immediateFirst() throws InterruptedException {
            return awaitOther("immediateFirst");
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String immediateSecond() throws InterruptedException {
            return awaitOther("immediateSecond");
        }

        static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
            throw (E) e;
        }

        private String awaitOther(String name) throws InterruptedException {
            invocations.incrementAndGet();
            latch.countDown();
            boolean concurrent = latch.await(500, TimeUnit.MILLISECONDS);
            return name + ":" + (concurrent ? "concurrent" : "sequential") + ":" + requestData.getValue();
        }
    }

    @CreatedAware
    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface Assistant {
        String chat(String message);

        Result<String> chatWithResult(String message);
    }

    @RegisterAiService(executeToolsConcurrently = true, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface RethrowingAssistant {
        String chat(String message);

        @HandleToolExecutionError
        static ToolErrorHandlerResult handle(ToolErrorContext context, Exception e) {
            Tools.sneakyThrow(e);
            return null;
        }
    }

    @RegisterAiService(executeToolsConcurrently = true, maxToolCallsPerResponse = 1, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface LimitedAssistant {
        String chat(String message);
    }

    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface SequentialAssistant {
        String chat(String message);
    }

    @RegisterAiService(tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface SequentialRethrowingAssistant {
        String chat(String message);

        @HandleToolExecutionError
        static ToolErrorHandlerResult handle(ToolErrorContext context, Exception e) {
            Tools.sneakyThrow(e);
            return null;
        }
    }

    @Inject
    Assistant assistant;

    @Inject
    LimitedAssistant limitedAssistant;

    @Inject
    RethrowingAssistant rethrowingAssistant;

    @Inject
    SequentialAssistant sequentialAssistant;

    @Inject
    SequentialRethrowingAssistant sequentialRethrowingAssistant;

    @Inject
    EventCollector eventCollector;

    @Inject
    RequestData requestData;

    @Inject
    Tools tools;

    @Inject
    ManagedExecutor managedExecutor;

    @BeforeEach
    void reset() {
        Tools.latch = new CountDownLatch(2);
        Tools.invocations.set(0);
        Tools.slowFinished = false;
        eventCollector.toolExecutedEvents.clear();
        lastMessages = null;
    }

    @Test
    @ActivateRequestContext
    void toolsRunConcurrentlyWithPropagatedContextAndOrderedResults() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::id).containsExactly("first", "second");
        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void toolsRunSequentiallyByDefault() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThat(sequentialAssistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("first:sequential:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void sequentialToolEventsAreFiredAsEachToolCompletes() {
        Tools.latch = new CountDownLatch(1);
        toolsToCall = List.of("first", "failingImmediately");
        requestData.setValue("propagated");

        assertThatThrownBy(() -> sequentialRethrowingAssistant.chat("hello")).isSameAs(Tools.RETHROWN_EXCEPTION);

        // the event of the first tool was fired before the second tool was executed
        assertThat(eventCollector.toolExecutedEvents).extracting(e -> e.request().name()).containsExactly("first");
    }

    @Test
    @ActivateRequestContext
    void failingToolDoesNotPreventOtherToolResults() {
        toolsToCall = List.of("failing", "second");
        requestData.setValue("propagated");

        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("boom", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void toolsWithinLimitAreExecutedBeforeLimitIsReported() {
        Tools.latch = new CountDownLatch(1);
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");

        assertThatThrownBy(() -> limitedAssistant.chat("hello"))
                .isInstanceOf(ToolCallsLimitExceededException.class);
        assertThat(Tools.invocations).hasValue(1);
    }

    @Test
    @ActivateRequestContext
    void programmaticServiceUsesProvidedExecutor() {
        toolsToCall = List.of("first", "second");
        requestData.setValue("propagated");
        AtomicInteger submissions = new AtomicInteger();
        Executor executor = command -> {
            submissions.incrementAndGet();
            managedExecutor.execute(command);
        };

        Assistant programmaticAssistant = AiServices.builder(Assistant.class)
                .chatModel(new ModelSupplier().get())
                .tools(tools)
                .executeToolsConcurrently(executor)
                .build();

        assertThat(programmaticAssistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
        assertThat(submissions).hasValue(2);
    }

    @Test
    @ActivateRequestContext
    void singleToolRunsOnTheCallerThread() {
        toolsToCall = List.of("currentThread");

        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly(Thread.currentThread().getName());
    }

    @Test
    @ActivateRequestContext
    void exceptionRethrownByErrorHandlerIsPropagatedAsIs() {
        toolsToCall = List.of("rethrown", "second");
        requestData.setValue("propagated");

        assertThatThrownBy(() -> rethrowingAssistant.chat("hello")).isSameAs(Tools.RETHROWN_EXCEPTION);
    }

    @Test
    @ActivateRequestContext
    void checkedExceptionRethrownByErrorHandlerIsPropagatedAsIs() {
        toolsToCall = List.of("checked", "second");
        requestData.setValue("propagated");

        assertThatThrownBy(() -> rethrowingAssistant.chat("hello")).isSameAs(Tools.CHECKED_EXCEPTION);
    }

    @Test
    @ActivateRequestContext
    void allToolsFinishBeforeFailureIsReported() {
        toolsToCall = List.of("failingImmediately", "slow");

        assertThatThrownBy(() -> rethrowingAssistant.chat("hello")).isSameAs(Tools.RETHROWN_EXCEPTION);
        // the tools share the request context of the caller, so none may still be running when the call returns
        assertThat(Tools.slowFinished).isTrue();
    }

    @Test
    @ActivateRequestContext
    void immediateReturnToolsReturnWithoutSecondLlmCall() {
        toolsToCall = List.of("immediateFirst", "immediateSecond");
        requestData.setValue("propagated");

        Result<String> result = assistant.chatWithResult("hello");

        assertThat(result.content()).isNull();
        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(result.toolExecutions()).extracting(ToolExecution::result)
                .containsExactly("immediateFirst:concurrent:propagated", "immediateSecond:concurrent:propagated");
        // the LLM was never called with the tool results
        assertThat(lastMessages).isNull();
    }

    @Test
    @ActivateRequestContext
    void immediateReturnToolCombinedWithRegularToolGoesBackToLlm() {
        toolsToCall = List.of("immediateFirst", "second");
        requestData.setValue("propagated");

        Result<String> result = assistant.chatWithResult("hello");

        assertThat(result.content()).isEqualTo("done");
        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("immediateFirst:concurrent:propagated", "second:concurrent:propagated");
    }

    private static List<ToolExecutionResultMessage> toolResults() {
        return lastMessages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }
}
