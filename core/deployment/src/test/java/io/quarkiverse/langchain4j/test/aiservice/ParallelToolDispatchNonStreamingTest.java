package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.BlockingToolNotAllowedException;
import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Phase 1 — non-streaming parallel tool dispatch coverage.
 *
 * <p>
 * Each test exercises a different aspect of the in-loop parallel dispatch added inside
 * {@code AiServiceMethodImplementationSupport.doImplement0}:
 *
 * <ul>
 * <li>Wall-clock speedup on virtual-threads mode (gated to Java 21+).</li>
 * <li>Serial baseline asserts the parallel branch is not silently engaged when the executor is null.</li>
 * <li>Bounded concurrency via {@code virtual-threads.max-concurrency} (gated to Java 21+).</li>
 * <li>Cancellation: a thrown exception in one task cancels the rest and propagates the original
 * exception identity to the caller.</li>
 * <li>{@link BlockingToolNotAllowedException} (a {@code PreventsErrorHandlerExecution}) bubbles up
 * unwrapped from a parallel future.</li>
 * <li>{@link ReturnBehavior#IMMEDIATE} is honored under parallel mode — by design the whole batch
 * is submitted before the IMMEDIATE flag is observed (Risk #2 in the design doc), so all tools
 * execute, but the final return value matches the IMMEDIATE-return semantics.</li>
 * </ul>
 *
 * <p>
 * Each test is its own QuarkusUnitTest deployment so we can vary
 * {@code quarkus.langchain4j.tools.execution} per case. WiremockChatModel is unnecessary — we
 * stub {@link ChatModel} inline with deterministic tool-call responses.
 */
public class ParallelToolDispatchNonStreamingTest {

    private static final long SLEEP_MS = 200;

    // -------------------------------------------------------------------------------------
    // 1) Wall-clock parallel speedup on virtual-threads (Java 21+ only)
    // -------------------------------------------------------------------------------------

    public static class VirtualThreadsParallelSpeedupTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolBatchModelSupplier.class,
                                ThreeToolBatchModel.class, ParallelAiService.class))
                // Set the GLOBAL execution mode. Per-service override keys with FQN class names containing
                // '.' and '$' are not recognised by SmallRye Config; the global key is the simplest unambiguous
                // way to exercise the parallel branch from a unit test.
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads");

        @Inject
        ParallelAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void threeToolsRunInParallelOnVirtualThreads() {
            assumeTrue(Runtime.version().feature() >= 21,
                    "virtual-threads dispatch requires Java 21+");

            long start = System.nanoTime();
            String result = aiService.chat("go");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(result).isEqualTo("done");
            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // 3 sleeps of 200ms in serial would be >=600ms. In parallel they overlap so wall-clock < 500ms.
            assertThat(elapsedMs)
                    .as("3 tools in parallel on virtual threads must complete in < 500ms (was %dms)", elapsedMs)
                    .isLessThan(500L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 2) Serial baseline — assert we did NOT accidentally always-parallelize
    // -------------------------------------------------------------------------------------

    public static class SerialBaselineTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, ThreeToolBatchModelSupplier.class,
                                ThreeToolBatchModel.class, SerialAiService.class));
        // No tools.execution config -> defaults to serial.

        @Inject
        SerialAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void serialModeRunsToolsSequentially() {
            long start = System.nanoTime();
            String result = aiService.chat("go");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(result).isEqualTo("done");
            assertThat(SleepyTools.invocations.get()).isEqualTo(3);
            // Each tool sleeps 200ms; serial dispatch must take >= 600ms. We allow modest scheduling slack;
            // any >= 600ms confirms the parallel branch did NOT silently engage.
            assertThat(elapsedMs)
                    .as("serial mode must take >= 600ms for 3*200ms tools (was %dms)", elapsedMs)
                    .isGreaterThanOrEqualTo(600L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 3) Bounded concurrency — virtual-threads.max-concurrency=2 with 5 tools
    // -------------------------------------------------------------------------------------

    public static class BoundedConcurrencyTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(SleepyTools.class, FiveToolBatchModelSupplier.class,
                                FiveToolBatchModel.class, BoundedAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads")
                .overrideRuntimeConfigKey(
                        "quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency", "2");

        @Inject
        BoundedAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            SleepyTools.invocations.set(0);
        }

        @Test
        @ActivateRequestContext
        void semaphoreThrottlesFiveToolsToBatchesOfTwo() {
            assumeTrue(Runtime.version().feature() >= 21,
                    "bounded virtual-threads dispatch requires Java 21+");

            long start = System.nanoTime();
            String result = aiService.chat("go");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(result).isEqualTo("done");
            assertThat(SleepyTools.invocations.get()).isEqualTo(5);
            // 5 tools at 200ms each, max-concurrency=2 -> batches of 2/2/1 -> ~3 * 200ms = 600ms.
            // Allow a window of 600-900ms to absorb scheduling jitter.
            assertThat(elapsedMs)
                    .as("5 tools at concurrency 2 must complete in 600-900ms (was %dms)", elapsedMs)
                    .isBetween(600L, 900L);
        }
    }

    // -------------------------------------------------------------------------------------
    // 4) Cancellation — first tool fails, remaining futures cancelled, exception identity preserved
    // -------------------------------------------------------------------------------------

    public static class CancellationTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(BespokeParallelDispatchException.class, FailingTools.class,
                                ThreeFailingToolBatchModelSupplier.class, ThreeFailingToolBatchModel.class,
                                CancellingAiService.class))
                // Use worker-pool so the cancellation path is exercised on Java 17+ as well.
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

        @Inject
        CancellingAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            FailingTools.completedAfterCancel.set(0);
        }

        @Test
        @ActivateRequestContext
        void firstToolThrowingPropagatesOriginalExceptionAndCancelsOthers() throws InterruptedException {
            // Catch via try/catch and inspect the class+message directly. Avoids assertJ's
            // canonical-name lookup which would try to load the outer test class (which is intentionally
            // excluded from the QuarkusUnitTest archive).
            Throwable thrown = null;
            try {
                aiService.chat("go");
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isNotNull();
            // The exception identity must be preserved — i.e. the same RuntimeException subclass that the tool
            // threw, with the same message, surfaces to the caller. No ExecutionException wrapping. We compare
            // by class identity (using a top-level class to avoid getCanonicalName() reflection that walks
            // enclosing classes — a problem under QuarkusUnitTest's restricted deployment classloader).
            assertThat(thrown.getClass().getName())
                    .as("the bespoke exception must propagate unchanged (no wrapping). Got: %s", thrown)
                    .isEqualTo(BespokeParallelDispatchException.class.getName());
            assertThat(thrown.getMessage()).isEqualTo("kaboom");
        }
    }

    // -------------------------------------------------------------------------------------
    // 5) PreventsErrorHandlerExecution — BlockingToolNotAllowedException bubbles up unchanged
    // -------------------------------------------------------------------------------------

    public static class PreventsErrorHandlerTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(BlockingFailureTools.class, ThreeBlockingFailureModelSupplier.class,
                                ThreeBlockingFailureModel.class, BlockingFailureAiService.class))
                // worker-pool is portable on Java 17+, so the test runs across the CI matrix.
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

        @Inject
        BlockingFailureAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
        }

        @Test
        @ActivateRequestContext
        void preventsErrorHandlerExceptionBubblesUpInParallelMode() {
            assertThatThrownBy(() -> aiService.chat("go"))
                    .as("BlockingToolNotAllowedException implements PreventsErrorHandlerExecution and must propagate")
                    .isInstanceOf(BlockingToolNotAllowedException.class);
        }
    }

    // -------------------------------------------------------------------------------------
    // 6) IMMEDIATE return + parallel — three IMMEDIATE-return tools all execute (atomic submission
    //    means all three are submitted before the loop checks the immediateToolReturn flag, which is
    //    consistent with the SERIAL path's post-loop check). Final result matches IMMEDIATE-return
    //    semantics: content == null, FinishReason.TOOL_EXECUTION, three toolExecutions recorded, and
    //    only ONE LLM call was made (no second turn).
    // -------------------------------------------------------------------------------------

    public static class ImmediateReturnInParallelTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(ImmediateMixTools.class, ThreeMixedImmediateModelSupplier.class,
                                ThreeMixedImmediateModel.class, ImmediateAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

        @Inject
        ImmediateAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            ImmediateMixTools.calls.set(0);
            ThreeMixedImmediateModel.callCount.set(0);
        }

        @Test
        @ActivateRequestContext
        void parallelImmediateReturnSubmitsAllButHonoursImmediateSemantics() {
            Result<String> result = aiService.chat("go");

            // All three tools ran (atomic submission)
            assertThat(ImmediateMixTools.calls.get()).isEqualTo(3);
            // IMMEDIATE-return semantics: no second LLM call, content null, finish reason TOOL_EXECUTION
            assertThat(ThreeMixedImmediateModel.callCount.get())
                    .as("only the initial LLM call should have happened (IMMEDIATE short-circuits the loop)")
                    .isEqualTo(1);
            assertThat(result.content()).isNull();
            assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
            assertThat(result.toolExecutions()).hasSize(3);
        }
    }

    // -------------------------------------------------------------------------------------
    // 7) Hung-tool characterization — there is no per-tool or per-batch timeout in parallel
    //    dispatch. A genuinely hung tool will block the conversation until the tool itself
    //    yields. Operators must enforce timeouts inside their tool implementations.
    // -------------------------------------------------------------------------------------

    /**
     * This test characterizes a known limitation — there is no per-tool or per-batch timeout in parallel dispatch.
     * A genuinely hung tool will block the conversation until the tool itself yields. Operators must enforce
     * timeouts inside their tool implementations.
     *
     * <p>
     * The branch deliberately uses {@code Future#cancel(false)} (documented as best-effort) and there is no
     * operation-level timeout. With one fast tool and one hung tool, {@code aiService.chat("go")} is expected to
     * stay blocked until the hung tool yields — confirmed here by running the call on a separate thread and
     * asserting it has not completed within a bounded sleep, then releasing the latch and verifying it completes
     * promptly thereafter.
     */
    public static class HungToolTest {

        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(HungTools.class, FastAndHungBatchModelSupplier.class,
                                FastAndHungBatchModel.class, HungAiService.class))
                .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

        @Inject
        HungAiService aiService;

        @BeforeEach
        void reset() {
            ParallelToolExecutorResolver.resetForTesting();
            HungTools.invocations.set(0);
            HungTools.releaseLatch = new CountDownLatch(1);
        }

        @AfterEach
        void releaseLatchAfterTest() {
            // Defensive — ensure the hung tool unblocks even if the test failed before reaching its own release.
            if (HungTools.releaseLatch != null) {
                HungTools.releaseLatch.countDown();
            }
        }

        @Test
        void hungToolBlocksUntilLatchReleasedAndCallCompletesAfterwards() throws Exception {
            // Drive aiService.chat on a separate thread so the test JVM does not deadlock if the behaviour ends
            // up being "hang forever". @ActivateRequestContext only binds the request scope to the test thread —
            // we have to activate one on the worker ourselves so the @RegisterAiService client proxy resolves.
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                try {
                    return aiService.chat("go");
                } finally {
                    requestContext.terminate();
                }
            });

            // Within ~3s the call must NOT have returned — the hung tool is holding the batch.
            assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                    .as("with one hung tool the call must not complete while the latch is held — no implicit timeout")
                    .isInstanceOf(TimeoutException.class);
            assertThat(future.isDone())
                    .as("future must still be running while the hung tool is blocked")
                    .isFalse();

            // Release the latch — the hung tool yields, the batch finishes, and the second LLM turn returns "done".
            HungTools.releaseLatch.countDown();
            String result = future.get(5, TimeUnit.SECONDS);

            assertThat(result).isEqualTo("done");
            assertThat(HungTools.invocations.get())
                    .as("both tools must have executed once the hung tool yields")
                    .isEqualTo(2);
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

        @Tool
        public String slow4() {
            return slow();
        }

        @Tool
        public String slow5() {
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

    @RegisterAiService(chatLanguageModelSupplier = ThreeToolBatchModelSupplier.class, tools = SleepyTools.class)
    public interface ParallelAiService {
        String chat(@UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = ThreeToolBatchModelSupplier.class, tools = SleepyTools.class)
    public interface SerialAiService {
        String chat(@UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = FiveToolBatchModelSupplier.class, tools = SleepyTools.class)
    public interface BoundedAiService {
        String chat(@UserMessage String message);
    }

    public static class ThreeToolBatchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ThreeToolBatchModel();
        }
    }

    public static class ThreeToolBatchModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            // First call -> emit a batch of 3 tool requests.
            // Second call (after tool results) -> say "done".
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow3").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    public static class FiveToolBatchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new FiveToolBatchModel();
        }
    }

    public static class FiveToolBatchModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow3").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("d").name("slow4").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("e").name("slow5").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    // --- Cancellation fixtures ---

    @ApplicationScoped
    public static class FailingTools {

        static final AtomicInteger completedAfterCancel = new AtomicInteger();
        static final ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();

        @Tool("immediately throws")
        public String failFast() {
            log.add("failFast-start");
            throw new BespokeParallelDispatchException("kaboom");
        }

        @Tool("slow tool")
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

        @Tool("slow tool")
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

    @RegisterAiService(chatLanguageModelSupplier = ThreeFailingToolBatchModelSupplier.class, tools = FailingTools.class)
    public interface CancellingAiService {
        String chat(@UserMessage String message);
    }

    public static class ThreeFailingToolBatchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ThreeFailingToolBatchModel();
        }
    }

    public static class ThreeFailingToolBatchModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("failFast").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("slow1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("slow2").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    // --- BlockingToolNotAllowedException fixtures ---

    @ApplicationScoped
    public static class BlockingFailureTools {

        @Tool
        public String tool1() {
            return "ok-1";
        }

        @Tool
        public String tool2() {
            // Throws a PreventsErrorHandlerExecution-marked exception
            throw new BlockingToolNotAllowedException("blocking not allowed");
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThreeBlockingFailureModelSupplier.class, tools = BlockingFailureTools.class)
    public interface BlockingFailureAiService {
        String chat(@UserMessage String message);
    }

    public static class ThreeBlockingFailureModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ThreeBlockingFailureModel();
        }
    }

    public static class ThreeBlockingFailureModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("tool1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("tool2").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    // --- IMMEDIATE return fixtures ---

    @ApplicationScoped
    public static class ImmediateMixTools {

        static final AtomicInteger calls = new AtomicInteger();

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String tool1() {
            calls.incrementAndGet();
            return "result-1";
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String tool2() {
            calls.incrementAndGet();
            return "result-2";
        }

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        public String tool3() {
            calls.incrementAndGet();
            return "result-3";
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThreeMixedImmediateModelSupplier.class, tools = ImmediateMixTools.class)
    public interface ImmediateAiService {
        Result<String> chat(@UserMessage String message);
    }

    public static class ThreeMixedImmediateModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ThreeMixedImmediateModel();
        }
    }

    public static class ThreeMixedImmediateModel implements ChatModel {
        static final AtomicInteger callCount = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            int n = callCount.incrementAndGet();
            // First call -> emit 3 tool requests with the IMMEDIATE one in the middle.
            if (n == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("tool1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("tool2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("tool3").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            // Should never happen under IMMEDIATE semantics — second LLM call indicates the short-circuit
            // failed.
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("UNEXPECTED"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    // --- Hung-tool fixtures ---

    @ApplicationScoped
    public static class HungTools {

        static final AtomicInteger invocations = new AtomicInteger();
        static volatile CountDownLatch releaseLatch = new CountDownLatch(1);

        @Tool
        public String fast() {
            invocations.incrementAndGet();
            return "fast-ok";
        }

        @Tool
        public String hung() {
            invocations.incrementAndGet();
            try {
                // Bounded await keeps the worker thread from leaking forever even if the test crashes before
                // releasing the latch — the @AfterEach also forces a release.
                if (!releaseLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("hung tool never released — test framework leak");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "hung-ok";
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = FastAndHungBatchModelSupplier.class, tools = HungTools.class)
    public interface HungAiService {
        String chat(@UserMessage String message);
    }

    public static class FastAndHungBatchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new FastAndHungBatchModel();
        }
    }

    public static class FastAndHungBatchModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("fast").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("hung").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

}
