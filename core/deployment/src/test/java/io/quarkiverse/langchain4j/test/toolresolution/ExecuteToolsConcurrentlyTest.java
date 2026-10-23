package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

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
                    .addClasses(Tools.class, RequestData.class, ModelSupplier.class));

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
        String value;
    }

    @ApplicationScoped
    public static class Tools {

        static volatile CountDownLatch latch;
        static final AtomicInteger invocations = new AtomicInteger();
        static final RuntimeException RETHROWN_EXCEPTION = new IllegalStateException("rethrown");

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

        private String awaitOther(String name) throws InterruptedException {
            invocations.incrementAndGet();
            latch.countDown();
            boolean concurrent = latch.await(500, TimeUnit.MILLISECONDS);
            return name + ":" + (concurrent ? "concurrent" : "sequential") + ":" + requestData.value;
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
            throw (RuntimeException) e;
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

    @Inject
    Assistant assistant;

    @Inject
    LimitedAssistant limitedAssistant;

    @Inject
    RethrowingAssistant rethrowingAssistant;

    @Inject
    SequentialAssistant sequentialAssistant;

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
        lastMessages = null;
    }

    @Test
    @ActivateRequestContext
    void toolsRunConcurrentlyWithPropagatedContextAndOrderedResults() {
        toolsToCall = List.of("first", "second");
        requestData.value = "propagated";

        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::id).containsExactly("first", "second");
        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("first:concurrent:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void toolsRunSequentiallyByDefault() {
        toolsToCall = List.of("first", "second");
        requestData.value = "propagated";

        assertThat(sequentialAssistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("first:sequential:propagated", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void failingToolDoesNotPreventOtherToolResults() {
        toolsToCall = List.of("failing", "second");
        requestData.value = "propagated";

        assertThat(assistant.chat("hello")).isEqualTo("done");

        assertThat(toolResults()).extracting(ToolExecutionResultMessage::text)
                .containsExactly("boom", "second:concurrent:propagated");
    }

    @Test
    @ActivateRequestContext
    void toolsWithinLimitAreExecutedBeforeLimitIsReported() {
        Tools.latch = new CountDownLatch(1);
        toolsToCall = List.of("first", "second");
        requestData.value = "propagated";

        assertThatThrownBy(() -> limitedAssistant.chat("hello"))
                .isInstanceOf(ToolCallsLimitExceededException.class);
        assertThat(Tools.invocations).hasValue(1);
    }

    @Test
    @ActivateRequestContext
    void programmaticServiceUsesProvidedExecutor() {
        toolsToCall = List.of("first", "second");
        requestData.value = "propagated";
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
        requestData.value = "propagated";

        assertThatThrownBy(() -> rethrowingAssistant.chat("hello")).isSameAs(Tools.RETHROWN_EXCEPTION);
    }

    @Test
    @ActivateRequestContext
    void immediateReturnToolsReturnWithoutSecondLlmCall() {
        toolsToCall = List.of("immediateFirst", "immediateSecond");
        requestData.value = "propagated";

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
        requestData.value = "propagated";

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
