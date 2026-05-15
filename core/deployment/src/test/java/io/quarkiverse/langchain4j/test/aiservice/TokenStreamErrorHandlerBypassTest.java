package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkus.test.QuarkusUnitTest;

/**
 * TokenStream + errorHandlerBypass parity. Pins that a tool throwing a
 * {@code PreventsErrorHandlerExecution}-marked exception propagates unchanged through the TokenStream
 * onError callback (no wrapping, no second LLM round). The non-streaming and Multi paths already
 * have equivalent assertions in {@code ParallelToolDispatchNonStreamingTest$CancellationTest} and
 * {@code ParallelToolDispatchMultiTest$CancellationDuringMultiTest}; this test fills the third leg
 * of the cross-path parity claim called out in the plan.
 */
public class TokenStreamErrorHandlerBypassTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BespokeParallelDispatchException.class, FailingTools.class,
                            FailingStreamingModelSupplier.class, FailingStreamingModel.class,
                            FailingTokenStreamAiService.class))
            // worker-pool keeps the test portable on Java 17+ and exercises the parallel dispatch path.
            .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

    @Inject
    FailingTokenStreamAiService aiService;

    @BeforeEach
    void reset() {
        ParallelToolExecutorResolver.resetForTesting();
        FailingTools.invocations.set(0);
        FailingStreamingModel.callCount.set(0);
    }

    @Test
    @ActivateRequestContext
    void marker_exception_from_tool_propagates_through_tokenstream_onError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicInteger completes = new AtomicInteger();

        aiService.chat("go")
                .onPartialResponse(s -> {
                })
                .onCompleteResponse(r -> {
                    completes.incrementAndGet();
                    latch.countDown();
                })
                .onError(t -> {
                    error.set(t);
                    latch.countDown();
                })
                .start();

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("TokenStream did not terminate within 30s")
                .isTrue();
        assertThat(completes.get())
                .as("onCompleteResponse must NOT fire when the marker exception bypasses the error handler")
                .isEqualTo(0);

        // The marker exception must propagate unchanged. We accept either the original or one Throwable
        // wrapper (CompletionException etc.) and unwrap to find the original cause.
        Throwable thrown = error.get();
        assertThat(thrown).isNotNull();
        Throwable cur = thrown;
        while (cur != null && !cur.getClass().getName()
                .equals(BespokeParallelDispatchException.class.getName())) {
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
        }
        assertThat(cur).as("marker exception must be findable in the cause chain. Got: %s", thrown).isNotNull();
        assertThat(cur.getClass().getName())
                .isEqualTo(BespokeParallelDispatchException.class.getName());
        assertThat(cur.getMessage()).isEqualTo("kaboom");

        // Only ONE LLM round should have happened: the marker exception aborts the loop before any
        // follow-up inference call.
        assertThat(FailingStreamingModel.callCount.get())
                .as("only the initial LLM call should have happened — bypass must short-circuit the loop")
                .isEqualTo(1);
    }

    // ===================================================================================
    // Test fixtures
    // ===================================================================================

    @ApplicationScoped
    public static class FailingTools {

        static final AtomicInteger invocations = new AtomicInteger();

        @Tool("immediately throws a PreventsErrorHandlerExecution-marked exception")
        public String failFast() {
            invocations.incrementAndGet();
            throw new BespokeParallelDispatchException("kaboom");
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = FailingStreamingModelSupplier.class, tools = FailingTools.class)
    public interface FailingTokenStreamAiService {
        TokenStream chat(@UserMessage String message);
    }

    public static class FailingStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new FailingStreamingModel();
        }
    }

    public static class FailingStreamingModel implements StreamingChatModel {
        static final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            callCount.incrementAndGet();
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("failFast").arguments("{}").build());
                handler.onCompleteToolCall(new CompleteToolCall(0, reqs.get(0)));
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("call-tools", reqs))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                // We should never get here when the marker exception aborts the loop.
                handler.onPartialResponse("UNEXPECTED");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("UNEXPECTED"))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }
}
