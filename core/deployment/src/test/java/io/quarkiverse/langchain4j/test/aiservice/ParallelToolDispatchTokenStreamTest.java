package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * Phase 2 — TokenStream parallel tool dispatch coverage.
 *
 * <p>
 * The Quarkus side merely passes the {@code parallelToolExecutor} to
 * {@code AiServiceTokenStreamParameters.toolExecutor(...)}; the upstream
 * {@code AiServiceStreamingResponseHandler.onCompleteToolCall} drives the
 * parallel dispatch when the executor is non-null and falls back to serial
 * dispatch when null.
 *
 * <ul>
 * <li>{@code virtualThreadsModeParallelizesTokenStreamTools} — three 200ms tools
 * complete in &lt; 600ms when {@code quarkus.langchain4j.tools.execution=virtual-threads}
 * (Java 21+, {@link org.junit.jupiter.api.Assumptions assume}-gated). On Java 17
 * the test is skipped because virtual-threads mode auto-downgrades to serial.</li>
 * <li>{@code serialModeKeepsTokenStreamSequential} — same three tools take
 * &gt;= 600ms with the default (serial) configuration.</li>
 * </ul>
 *
 * <p>
 * Note: {@code @RegisterAiService}-driven services bypass
 * {@code QuarkusAiServicesFactory.QuarkusAiServices.build()}; the executor is
 * resolved for them by {@code AiServicesRecorder.createDeclarativeAiService},
 * which assigns {@code aiServiceContext.parallelToolExecutor} before returning
 * the synthetic context. This test exercises the declarative path end-to-end.
 */
public class ParallelToolDispatchTokenStreamTest {

    private static final long SLEEP_MS = 200;

    // -------------------------------------------------------------------------------------
    // 1) virtual-threads mode — parallel TokenStream tool dispatch
    // -------------------------------------------------------------------------------------

    public static class VirtualThreadsTokenStreamParallelTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolStreamingModelSupplier.class,
                                ThreeToolStreamingModel.class, ParallelStreamAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads");

        @Inject
        ParallelStreamAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void virtualThreadsModeParallelizesTokenStreamTools() throws InterruptedException {
            assumeTrue(Runtime.version().feature() >= 21,
                    "virtual-threads dispatch requires Java 21+");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            long start = System.nanoTime();
            aiService.chat("go")
                    .onPartialResponse(s -> {
                    })
                    .onCompleteResponse(r -> latch.countDown())
                    .onError(t -> {
                        error.set(t);
                        latch.countDown();
                    })
                    .start();
            assertThat(latch.await(30, TimeUnit.SECONDS))
                    .as("TokenStream did not complete within 30s").isTrue();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            assertThat(error.get()).isNull();

            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // 3 tools at 200ms each in serial would be >= 600ms. In parallel they overlap to < 600ms.
            assertThat(elapsedMs)
                    .as("3 streaming tools in parallel must complete in < 600ms (was %dms)", elapsedMs)
                    .isLessThan(600L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 2) Default (serial) mode — TokenStream stays sequential
    // -------------------------------------------------------------------------------------

    public static class SerialTokenStreamBaselineTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolStreamingModelSupplier.class,
                                ThreeToolStreamingModel.class, SerialStreamAiService.class));
        // No tools.execution config -> defaults to serial.

        @Inject
        SerialStreamAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void serialModeKeepsTokenStreamSequential() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            long start = System.nanoTime();
            aiService.chat("go")
                    .onPartialResponse(s -> {
                    })
                    .onCompleteResponse(r -> latch.countDown())
                    .onError(t -> {
                        error.set(t);
                        latch.countDown();
                    })
                    .start();
            assertThat(latch.await(30, TimeUnit.SECONDS))
                    .as("TokenStream did not complete within 30s").isTrue();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            assertThat(error.get()).isNull();

            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // Serial dispatch of 3*200ms tools must take >= 600ms, confirming the parallel path
            // did not silently engage when no executor was configured.
            assertThat(elapsedMs)
                    .as("serial streaming mode must take >= 600ms for 3*200ms tools (was %dms)", elapsedMs)
                    .isGreaterThanOrEqualTo(600L);
        }
    }

    // ===================================================================================
    // Shared test fixtures
    // ===================================================================================

    @ApplicationScoped
    public static class SleepyTools {

        static final AtomicInteger invocations = new AtomicInteger();

        @Tool
        public String slow1() {
            return slow();
        }

        @Tool
        public String slow2() {
            return slow();
        }

        @Tool
        public String slow3() {
            return slow();
        }

        private static String slow() {
            invocations.incrementAndGet();
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "ok";
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ThreeToolStreamingModelSupplier.class, tools = SleepyTools.class)
    public interface ParallelStreamAiService {
        TokenStream chat(@UserMessage String message);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ThreeToolStreamingModelSupplier.class, tools = SleepyTools.class)
    public interface SerialStreamAiService {
        TokenStream chat(@UserMessage String message);
    }

    public static class ThreeToolStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new ThreeToolStreamingModel();
        }
    }

    public static class ThreeToolStreamingModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                // First call -> emit a batch of 3 tool requests. We must call onCompleteToolCall
                // for each tool request before onCompleteResponse — that's the upstream hook
                // (AiServiceStreamingResponseHandler.onCompleteToolCall) which submits the
                // execution to the parallel toolExecutor when one is configured.
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow3").arguments("{}").build());
                for (int i = 0; i < reqs.size(); i++) {
                    handler.onCompleteToolCall(new CompleteToolCall(i, reqs.get(i)));
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("call-tools", reqs))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                // Tool results are in -> emit final answer.
                handler.onPartialResponse("done");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("done"))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }
}
