package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Multi (TokenStreamMulti) parallel tool dispatch coverage.
 *
 * <p>
 * Multi flows through the upstream {@code AiServiceTokenStream} / {@code ToolBatchDispatcher}, with
 * the executor wired by Quarkus via {@code AiServices.executeToolsConcurrently}. This pins:
 * siblings cancelled on first failure, {@code PreventsErrorHandlerExecution}-marked exceptions
 * propagate unchanged through the upstream {@code errorHandlerBypass}, atomic
 * {@code maxToolCallsPerResponse} rejection, and gather-thread memory ordering.
 *
 * <ul>
 * <li>{@code virtualThreadsModeParallelizesMultiTools} — three 200ms tools complete in &lt; 600ms when
 * {@code quarkus.langchain4j.tools.execution=virtual-threads} (Java 21+ assumeTrue-gated).</li>
 * <li>{@code serialModeKeepsMultiSequential} — same three tools take &gt;= 600ms with the default
 * (serial) configuration.</li>
 * <li>{@code cancellationDuringMultiParallelDispatch} — first tool throws a
 * {@link BespokeParallelDispatchException} (a {@code PreventsErrorHandlerExecution}); the original
 * exception type bubbles out via the Multi error path, and remaining futures are cancelled (no
 * late memory writes).</li>
 * <li>{@code committableChatMemoryOrderingUnderParallel} — five tools each sleeping a randomised
 * 50–300ms; assert {@code ToolExecutionResultMessage} entries arrive at the model's second turn in
 * original request order (Java 21+ gated).</li>
 * </ul>
 */
public class ParallelToolDispatchMultiTest {

    private static final long SLEEP_MS = 200;

    // -------------------------------------------------------------------------------------
    // 1) virtual-threads mode — parallel Multi tool dispatch
    // -------------------------------------------------------------------------------------

    public static class VirtualThreadsMultiParallelTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolStreamingModelSupplier.class,
                                ThreeToolStreamingModel.class, ParallelMultiAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads");

        @Inject
        ParallelMultiAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void virtualThreadsModeParallelizesMultiTools() {
            assumeTrue(Runtime.version().feature() >= 21,
                    "virtual-threads dispatch requires Java 21+");

            long start = System.nanoTime();
            List<String> chunks = aiService.chat("go").collect().asList().await().indefinitely();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(chunks).contains("done");
            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // 3 tools at 200ms each in serial would be >= 600ms. In parallel they overlap to < 600ms.
            assertThat(elapsedMs)
                    .as("3 Multi tools in parallel must complete in < 600ms (was %dms)", elapsedMs)
                    .isLessThan(600L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 2) Default (serial) mode — Multi stays sequential
    // -------------------------------------------------------------------------------------

    public static class SerialMultiBaselineTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolStreamingModelSupplier.class,
                                ThreeToolStreamingModel.class, SerialMultiAiService.class));
        // No tools.execution config -> defaults to serial.

        @Inject
        SerialMultiAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void serialModeKeepsMultiSequential() {
            long start = System.nanoTime();
            List<String> chunks = aiService.chat("go").collect().asList().await().indefinitely();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(chunks).contains("done");
            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // Serial dispatch of 3*200ms tools must take >= 600ms, confirming the parallel path
            // did not silently engage when no executor was configured.
            assertThat(elapsedMs)
                    .as("serial Multi mode must take >= 600ms for 3*200ms tools (was %dms)", elapsedMs)
                    .isGreaterThanOrEqualTo(600L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 3) Cancellation — first tool throws BespokeParallelDispatchException (PreventsErrorHandlerExecution).
    //    The exception type must propagate unchanged through the Multi error path, and the slow siblings
    //    must be cancelled — no late memory writes.
    // -------------------------------------------------------------------------------------

    public static class CancellationDuringMultiTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(BespokeParallelDispatchException.class, FailingMultiTools.class,
                                ThreeFailingToolStreamingModelSupplier.class,
                                ThreeFailingToolStreamingModel.class, CancellingMultiAiService.class))
                // worker-pool is portable on Java 17+, so the test runs across the CI matrix.
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

        @Inject
        CancellingMultiAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            FailingMultiTools.completedAfterCancel.set(0);
            FailingMultiTools.log.clear();
            ThreeFailingToolStreamingModel.callCount.set(0);
        }

        @Test
        @ActivateRequestContext
        void cancellationDuringMultiParallelDispatch() {
            // Subscribe and collect via Mutiny. The exception must surface via the Multi error path.
            // We compare by class name (not assertJ canonical-name lookup) to avoid resolving the outer test
            // class under QuarkusUnitTest's restricted deployment classloader.
            Throwable thrown = null;
            try {
                aiService.chat("go").collect().asList().await().indefinitely();
            } catch (Throwable t) {
                // Mutiny may wrap in CompletionException; unwrap to find the original cause.
                Throwable cur = t;
                while (cur != null && !cur.getClass().getName()
                        .equals(BespokeParallelDispatchException.class.getName())) {
                    if (cur.getCause() == cur) {
                        break;
                    }
                    cur = cur.getCause();
                }
                thrown = cur != null ? cur : t;
            }
            assertThat(thrown).isNotNull();
            assertThat(thrown.getClass().getName())
                    .as("the bespoke exception must propagate unchanged through Multi (no extra wrapping). Got: %s",
                            thrown)
                    .isEqualTo(BespokeParallelDispatchException.class.getName());
            assertThat(thrown.getMessage()).isEqualTo("kaboom");

            // Only ONE LLM call should have been made — the failure short-circuits the second turn.
            assertThat(ThreeFailingToolStreamingModel.callCount.get())
                    .as("exception must abort the conversation; no second LLM round")
                    .isEqualTo(1);
            // No late memory writes / completed slow tools — siblings were cancelled on failure.
            assertThat(FailingMultiTools.completedAfterCancel.get())
                    .as("sibling tools must be cancelled before they complete; saw %d post-cancel completions",
                            FailingMultiTools.completedAfterCancel.get())
                    .isEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------------------
    // 4) CommittableChatMemory ordering — five tools sleep a randomised 50-300ms each. Their
    //    ToolExecutionResultMessage entries (recorded on the gather thread) must appear in the
    //    second LLM call in ORIGINAL request order, not completion order.
    // -------------------------------------------------------------------------------------

    public static class CommittableChatMemoryOrderingTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(OrderedTools.class, FiveToolStreamingModelSupplier.class,
                                FiveToolStreamingModel.class, OrderedMultiAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads");

        @Inject
        OrderedMultiAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            OrderedTools.invocations.set(0);
            FiveToolStreamingModel.observedSecondTurnTools.clear();
        }

        @Test
        @ActivateRequestContext
        void committableChatMemoryOrderingUnderParallel() {
            assumeTrue(Runtime.version().feature() >= 21,
                    "virtual-threads dispatch requires Java 21+");

            List<String> chunks = aiService.chat("go").collect().asList().await().indefinitely();

            assertThat(chunks).contains("done");
            assertThat(OrderedTools.invocations.get()).isEqualTo(5);
            // The model captured the tool result messages on the SECOND turn — they should be in original
            // request order: tool1, tool2, tool3, tool4, tool5 (regardless of completion order).
            assertThat(FiveToolStreamingModel.observedSecondTurnTools)
                    .as("ToolExecutionResultMessage entries must arrive in original request order")
                    .containsExactly("tool1", "tool2", "tool3", "tool4", "tool5");
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
    public interface ParallelMultiAiService {
        Multi<String> chat(@UserMessage String message);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ThreeToolStreamingModelSupplier.class, tools = SleepyTools.class)
    public interface SerialMultiAiService {
        Multi<String> chat(@UserMessage String message);
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
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow3").arguments("{}").build());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("call-tools", reqs))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                handler.onPartialResponse("done");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("done"))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    // --- Cancellation fixtures ---

    @ApplicationScoped
    public static class FailingMultiTools {

        static final AtomicInteger completedAfterCancel = new AtomicInteger();
        static final ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();

        @Tool("immediately throws")
        public String failFast() {
            log.add("failFast-start");
            throw new BespokeParallelDispatchException("kaboom");
        }

        @Tool("slow tool 1")
        public String slow1() {
            log.add("slow1-start");
            try {
                Thread.sleep(SLEEP_MS * 2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.add("slow1-interrupted");
                return "interrupted";
            }
            log.add("slow1-completed");
            completedAfterCancel.incrementAndGet();
            return "ok";
        }

        @Tool("slow tool 2")
        public String slow2() {
            log.add("slow2-start");
            try {
                Thread.sleep(SLEEP_MS * 2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.add("slow2-interrupted");
                return "interrupted";
            }
            log.add("slow2-completed");
            completedAfterCancel.incrementAndGet();
            return "ok";
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ThreeFailingToolStreamingModelSupplier.class, tools = FailingMultiTools.class)
    public interface CancellingMultiAiService {
        Multi<String> chat(@UserMessage String message);
    }

    public static class ThreeFailingToolStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new ThreeFailingToolStreamingModel();
        }
    }

    public static class ThreeFailingToolStreamingModel implements StreamingChatModel {
        static final AtomicInteger callCount = new AtomicInteger();

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            callCount.incrementAndGet();
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("failFast").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow2").arguments("{}").build());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("call-tools", reqs))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                handler.onPartialResponse("done");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("done"))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.STOP)
                        .build());
            }
        }
    }

    // --- Ordering fixtures ---

    @ApplicationScoped
    public static class OrderedTools {

        static final AtomicInteger invocations = new AtomicInteger();

        @Tool
        public String tool1() {
            return jitter("tool1");
        }

        @Tool
        public String tool2() {
            return jitter("tool2");
        }

        @Tool
        public String tool3() {
            return jitter("tool3");
        }

        @Tool
        public String tool4() {
            return jitter("tool4");
        }

        @Tool
        public String tool5() {
            return jitter("tool5");
        }

        private static String jitter(String name) {
            invocations.incrementAndGet();
            // Randomised 50-300ms sleep to ensure completion order != request order.
            long delay = 50 + ThreadLocalRandom.current().nextLong(250);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return name + "-result";
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = FiveToolStreamingModelSupplier.class, tools = OrderedTools.class)
    public interface OrderedMultiAiService {
        Multi<String> chat(@UserMessage String message);
    }

    public static class FiveToolStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new FiveToolStreamingModel();
        }
    }

    public static class FiveToolStreamingModel implements StreamingChatModel {

        // Captures the tool names in the order their ToolExecutionResultMessages appear in the
        // chat request on the SECOND turn — i.e. the order the gather thread wrote them to memory.
        static final List<String> observedSecondTurnTools = new CopyOnWriteArrayList<>();

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("tool1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("tool2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("tool3").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("d").name("tool4").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("e").name("tool5").arguments("{}").build());
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("call-tools", reqs))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build());
            } else {
                // Snapshot the tool result message ordering — these were written to memory on the gather
                // thread by Phase 3, in original request order.
                for (ChatMessage m : messages) {
                    if (m instanceof ToolExecutionResultMessage trm) {
                        observedSecondTurnTools.add(trm.toolName());
                    }
                }
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
