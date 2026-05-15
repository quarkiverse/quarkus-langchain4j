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
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;

/**
 * End-to-end integration test that exercises parallel virtual-thread tool dispatch against
 * the real Anthropic API. The AI service is asked to call a single tool three times in a
 * single turn; that tool blocks for ~10 seconds by hitting httpbin.org/delay/10. With the
 * virtual-thread executor configured, the three blocking calls overlap and the batch
 * finishes in ~10 seconds; serial dispatch would take ~30 seconds.
 * <p>
 * Skipped automatically when {@code ANTHROPIC_API_KEY} is not set. Requires Java 21+ for
 * virtual-thread dispatch.
 */
@EnabledIf("hasAnthropicApiKey")
@EnabledForJreRange(min = JRE.JAVA_21)
class AnthropicVirtualThreadToolDispatchITTest {

    private static final String API_KEY = System.getProperty("anthropic.api.key", System.getenv("ANTHROPIC_API_KEY"));

    static boolean hasAnthropicApiKey() {
        return API_KEY != null && !API_KEY.isBlank();
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DelayAssistant.class, HttpDelayTool.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", "claude-sonnet-4-5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.max-tokens", "2048")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.timeout", "120s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-requests", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-responses", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution", "virtual-threads");

    @Inject
    DelayAssistant assistant;

    @Test
    void virtualThreadToolBatchExecutesInParallelWhenExecutorIsVirtual() {
        HttpDelayTool.reset();

        long wallStart = System.currentTimeMillis();
        String response = assistant.runDelays("alpha", "beta", "gamma")
                .collect().asList()
                .map(parts -> String.join("", parts))
                .await().atMost(Duration.ofMinutes(3));
        long wallElapsedMs = System.currentTimeMillis() - wallStart;

        List<HttpDelayTool.Invocation> invocations = HttpDelayTool.snapshot();

        System.out.println("=== AnthropicVirtualThreadToolDispatchITTest ===");
        System.out.println("Final response: " + response);
        System.out.println("Wall-clock total: " + wallElapsedMs + " ms");
        for (HttpDelayTool.Invocation inv : invocations) {
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
        for (HttpDelayTool.Invocation inv : invocations) {
            labels.add(inv.label);
        }
        assertThat(labels)
                .as("each invocation should carry one of the three throwaway labels")
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");

        assertThat(invocations)
                .as("each invocation should run on a virtual thread")
                .allSatisfy(inv -> assertThat(isVirtualThread(inv.thread))
                        .as("invocation %s thread %s is virtual", inv.label, inv.threadName)
                        .isTrue());

        Set<String> threadNames = new HashSet<>();
        for (HttpDelayTool.Invocation inv : invocations) {
            threadNames.add(inv.threadName);
        }
        assertThat(threadNames)
                .as("each invocation should run on a distinct virtual thread")
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
    public interface DelayAssistant {

        @SystemMessage("You orchestrate slow data lookups. When the user gives you several "
                + "items, ALWAYS issue every needed tool call in the same response so they "
                + "execute in parallel. Never call tools sequentially when the inputs are "
                + "independent.")
        @ToolBox(HttpDelayTool.class)
        @UserMessage("Call the http_delay tool three times - once with label='{first}', "
                + "once with label='{second}', once with label='{third}'. Issue all three "
                + "tool calls in this single turn so they run in parallel. Once you have "
                + "the three results, summarize them in one short sentence.")
        Multi<String> runDelays(String first, String second, String third);
    }

    @ApplicationScoped
    public static class HttpDelayTool {

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
        @RunOnVirtualThread
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
