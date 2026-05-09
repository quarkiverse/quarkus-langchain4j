package io.quarkiverse.langchain4j.anthropic.deployment.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Vertx;

/**
 * Counterpart to {@link AnthropicVirtualThreadToolDispatchITTest}. The tool method is plain
 * {@code @Blocking} (no {@code @RunOnVirtualThread}). With the {@code worker-pool} executor
 * (a {@code ManagedExecutor}, platform threads), the dispatcher fans the three 10s tool
 * calls out and the batch should complete in ~10s rather than ~30s.
 * <p>
 * The test asserts parallelism via wall-clock + per-invocation timestamps and distinct
 * thread names, but does not assume any specific carrier (Vert.x worker / executor pool /
 * etc.) - just that the tools ran on platform (non-virtual) threads in parallel.
 * <p>
 * Skipped automatically when {@code ANTHROPIC_API_KEY} is not set. Java 21 guard mirrors
 * the parallel test for symmetry.
 */
@EnabledIf("hasAnthropicApiKey")
@EnabledForJreRange(min = JRE.JAVA_21)
class AnthropicVertxWorkerToolDispatchITTest {

    private static final String API_KEY = System.getProperty("anthropic.api.key", System.getenv("ANTHROPIC_API_KEY"));

    static boolean hasAnthropicApiKey() {
        return API_KEY != null && !API_KEY.isBlank();
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SerialDelayAssistant.class, BlockingHttpDelayTool.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", "claude-sonnet-4-5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.max-tokens", "2048")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.timeout", "120s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-responses", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "worker-pool");

    @Inject
    SerialDelayAssistant assistant;

    @Inject
    Vertx vertx;

    @Test
    void blockingToolBatchExecutesInParallelOnManagedExecutor() throws InterruptedException {
        BlockingHttpDelayTool.reset();

        long wallStart = System.currentTimeMillis();
        String response = runDelaysFromVertxContext("alpha", "beta", "gamma");
        long wallElapsedMs = System.currentTimeMillis() - wallStart;

        List<BlockingHttpDelayTool.Invocation> invocations = BlockingHttpDelayTool.snapshot();

        System.out.println("=== AnthropicVertxWorkerToolDispatchITTest ===");
        System.out.println("Final response: " + response);
        System.out.println("Wall-clock total: " + wallElapsedMs + " ms");
        for (BlockingHttpDelayTool.Invocation inv : invocations) {
            System.out.printf(
                    "  tool[%s] thread=%s start=+%d ms end=+%d ms duration=%d ms%n",
                    inv.label,
                    inv.threadName,
                    inv.startMs - wallStart,
                    inv.endMs - wallStart,
                    inv.endMs - inv.startMs);
        }

        assertThat(invocations)
                .as("LLM should have invoked the http_delay tool exactly three times")
                .hasSize(3);

        Set<String> labels = new HashSet<>();
        for (BlockingHttpDelayTool.Invocation inv : invocations) {
            labels.add(inv.label);
        }
        assertThat(labels)
                .as("each invocation should carry one of the three throwaway labels")
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");

        assertThat(invocations)
                .as("plain @Blocking tools should not run on virtual threads under the managed executor")
                .allSatisfy(inv -> assertThat(isVirtualThread(inv.thread))
                        .as("invocation %s thread %s is virtual", inv.label, inv.threadName)
                        .isFalse());

        Set<String> threadNames = new HashSet<>();
        for (BlockingHttpDelayTool.Invocation inv : invocations) {
            threadNames.add(inv.threadName);
        }
        assertThat(threadNames)
                .as("each invocation should run on a distinct platform thread")
                .hasSize(3);

        long earliestStart = invocations.stream().mapToLong(i -> i.startMs).min().orElseThrow();
        long latestStart = invocations.stream().mapToLong(i -> i.startMs).max().orElseThrow();
        long latestEnd = invocations.stream().mapToLong(i -> i.endMs).max().orElseThrow();
        long batchSpan = latestEnd - earliestStart;
        long startSpread = latestStart - earliestStart;

        assertThat(startSpread)
                .as("all three tools should start within a small window if dispatched in parallel "
                        + "(observed start spread = %d ms)", startSpread)
                .isLessThan(2_500);

        assertThat(batchSpan)
                .as("three 10s tool calls dispatched in parallel should complete in ~10s wall-clock "
                        + "(observed batch span = %d ms; serial would be ~30s)", batchSpan)
                .isLessThan(20_000);
    }

    private String runDelaysFromVertxContext(String first, String second, String third) throws InterruptedException {
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        VertxContext.getOrCreateDuplicatedContext(vertx).executeBlocking(() -> {
            Arc.container().requestContext().activate();
            try {
                assistant.runDelays(first, second, third)
                        .collect().asList()
                        .map(parts -> String.join("", parts))
                        .subscribe().with(
                                item -> {
                                    response.set(item);
                                    done.countDown();
                                },
                                t -> {
                                    failure.set(t);
                                    done.countDown();
                                });
            } catch (Throwable t) {
                failure.set(t);
                done.countDown();
            } finally {
                Arc.container().requestContext().deactivate();
            }
            return null;
        }, /* ordered= */ false);

        assertThat(done.await(3, TimeUnit.MINUTES)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("Anthropic delay call failed", failure.get());
        }
        return response.get();
    }

    /**
     * Source-level Java 17 compatibility: {@code Thread#isVirtual} only exists on Java 21+,
     * and this class is gated to Java 21 by {@link EnabledForJreRange}, so reflection here
     * is safe at runtime.
     */
    private static boolean isVirtualThread(Thread thread) {
        try {
            return (Boolean) Thread.class.getMethod("isVirtual").invoke(thread);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Thread#isVirtual reflection failed on Java 21+", e);
        }
    }

    @RegisterAiService
    @ApplicationScoped
    public interface SerialDelayAssistant {

        @SystemMessage("You orchestrate slow data lookups. When the user gives you several "
                + "items, ALWAYS issue every needed tool call in the same response so they "
                + "execute in parallel. Never call tools sequentially when the inputs are "
                + "independent.")
        @ToolBox(BlockingHttpDelayTool.class)
        @UserMessage("Call the http_delay tool three times - once with label='{first}', "
                + "once with label='{second}', once with label='{third}'. Issue all three "
                + "tool calls in this single turn so they run in parallel. Once you have "
                + "the three results, summarize them in one short sentence.")
        Multi<String> runDelays(String first, String second, String third);
    }

    /**
     * Same shape as {@code HttpDelayTool} in the parallel test, but deliberately omits
     * {@code @RunOnVirtualThread}. The build-time execution model resolves to BLOCKING
     * (the default for blocking-signature tool methods).
     */
    @ApplicationScoped
    public static class BlockingHttpDelayTool {

        public static final class Invocation {
            public final String label;
            public final long startMs;
            public final long endMs;
            public final Thread thread;
            public final String threadName;

            Invocation(String label, long startMs, long endMs, Thread thread) {
                this.label = label;
                this.startMs = startMs;
                this.endMs = endMs;
                this.thread = thread;
                this.threadName = thread.toString();
            }
        }

        private static final CopyOnWriteArrayList<Invocation> INVOCATIONS = new CopyOnWriteArrayList<>();
        private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        static void reset() {
            INVOCATIONS.clear();
        }

        static List<Invocation> snapshot() {
            return List.copyOf(INVOCATIONS);
        }

        @Tool(name = "http_delay", value = "Issues a slow remote call that blocks for ~10 seconds "
                + "and returns the JSON body. Use this whenever you need to fetch data for a label.")
        public String httpDelay(String label) {
            long startMs = System.currentTimeMillis();
            Thread thread = Thread.currentThread();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://httpbin.org/delay/10"))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                long endMs = System.currentTimeMillis();
                INVOCATIONS.add(new Invocation(label, startMs, endMs, thread));
                int status = response.statusCode();
                return "{\"label\":\"" + label + "\",\"status\":" + status + ",\"durationMs\":"
                        + (endMs - startMs) + "}";
            } catch (Exception e) {
                long endMs = System.currentTimeMillis();
                INVOCATIONS.add(new Invocation(label, startMs, endMs, thread));
                throw new RuntimeException("http_delay failed for label=" + label, e);
            }
        }
    }
}
