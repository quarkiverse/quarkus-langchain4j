package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.Executor;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Regression coverage for Finding 6 of the parallel-tool-execution review: invalid parallel-execution config must
 * fail loudly instead of silently degrading to serial dispatch.
 *
 * <p>
 * The two call sites that resolve the parallel executor — {@code QuarkusAiServicesFactory.build()} and
 * {@code AiServicesRecorder.createDeclarativeAiService} — previously wrapped resolution in
 * {@code catch (RuntimeException e) { warn-and-continue; }}. That catch was intended as an environment-driven
 * safety net (Java 17 cannot construct virtual threads) but also swallowed real misconfiguration:
 * <ul>
 * <li>{@code quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency=-5} — invalid concurrency value.</li>
 * <li>An unknown executor mode name (already rejected by SmallRye Config at config-load time, but the broad catch
 * would also have swallowed any future {@code IllegalStateException} thrown by the resolver itself).</li>
 * </ul>
 * The fix narrows both catches to {@link UnsupportedOperationException} only — the conventional Java signal for
 * "feature exists but is not usable in this JVM/runtime". Every other RuntimeException now propagates, so
 * misconfiguration fails the application at startup with a clear message.
 *
 * <p>
 * This test validates the fix at the resolver level; it does not boot a full AiService because the config we are
 * exercising would (correctly) make startup fail. The behaviour at the catch sites is asserted by the resolver
 * surfacing the right exception type — the call sites no longer catch it.
 */
public class ParallelToolExecutorResolverFailLoudTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // Force virtual-threads mode for "vtSvc" so the resolver actually constructs the BoundedExecutor and
            // hits the invalid concurrency value. Global mode stays serial so unrelated tests (and the default
            // fallthrough assertion below) remain unaffected.
            .overrideRuntimeConfigKey("quarkus.langchain4j.vtSvc.tools.execution", "virtual-threads")
            // -5 is invalid: BoundedExecutor's constructor rejects any maxConcurrency <= 0 with
            // IllegalArgumentException. SmallRye accepts the value at config-load (the field is a plain int);
            // validation happens when the resolver builds the executor.
            .overrideRuntimeConfigKey("quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency", "-5");

    @Inject
    LangChain4jConfig config;

    @BeforeEach
    void resetCache() {
        ParallelToolExecutorResolver.resetForTesting();
    }

    @AfterEach
    void clearCache() {
        ParallelToolExecutorResolver.resetForTesting();
    }

    /**
     * Invalid {@code virtual-threads.max-concurrency} must fail LOUDLY. Prior to the fix the catch sites swallowed
     * this and silently fell back to serial, leaving users with no diagnostic.
     *
     * <p>
     * Only meaningful on Java 21+: on earlier JVMs the resolver downgrades virtual-threads -> serial inside
     * {@code applyJavaVersionFallback} before reaching {@code BoundedExecutor}, so the bad concurrency value is
     * never read. That is the legitimate environment-driven fallback the fix preserves.
     */
    @Test
    void invalidConcurrencyValueFailsLoudly() {
        assumeTrue(Runtime.version().feature() >= 21,
                "This assertion requires Java 21+ — on earlier JVMs the resolver downgrades VT mode to serial "
                        + "before constructing BoundedExecutor, so the invalid concurrency value is never reached.");
        assertThatThrownBy(() -> ParallelToolExecutorResolver.resolve("vtSvc", config))
                .as("invalid virtual-threads.max-concurrency must surface as IllegalArgumentException, not a silent "
                        + "fallback to serial")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency must be > 0");
    }

    /**
     * The Java-version downgrade path is environment-driven and must continue to work silently (well, with a
     * single startup warning) — users on Java 17 with the default config are not making a configuration error.
     * On Java 17-20 the resolver returns {@code null} for "vtSvc" (= serial) without throwing, even though the
     * concurrency value is invalid, because the bad value is never read.
     */
    @Test
    void java17FallbackStillWorksDespiteInvalidConcurrency() {
        assumeTrue(Runtime.version().feature() < 21,
                "This assertion only applies on Java <21 where VT mode downgrades to serial at the resolver level.");
        assertThatCode(() -> {
            Executor resolved = ParallelToolExecutorResolver.resolve("vtSvc", config);
            assertThat(resolved).as("Java <21 must downgrade VT mode to serial regardless of concurrency value")
                    .isNull();
        }).doesNotThrowAnyException();
    }
}
