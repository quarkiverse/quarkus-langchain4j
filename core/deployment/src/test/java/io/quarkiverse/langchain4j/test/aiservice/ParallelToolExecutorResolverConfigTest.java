package io.quarkiverse.langchain4j.test.aiservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkiverse.langchain4j.runtime.aiservice.VertxContextAwareExecutor;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the {@link ParallelToolExecutorResolver} contract end-to-end against the actual
 * {@link LangChain4jConfig} mapping wired by SmallRye Config:
 *
 * <ol>
 * <li>Default-mode fallthrough: with no global / per-service config, the resolver returns {@code null} (= serial).</li>
 * <li>Per-service key {@code quarkus.langchain4j.<service>.tools.execution} resolves and overrides the global default
 * even when the requested mode would downgrade ({@code virtual-threads} -> serial on Java <21 still proves the key
 * was read; the assertion accepts either {@code null} or a {@code VertxContextAwareExecutor} so the test is portable
 * across JVMs).</li>
 * <li>Sibling-name safety: a service called "myAssistant" does not collide with the {@code tools}, {@code ai-service},
 * {@code guardrails}, or {@code tracing} sibling fields under {@code quarkus.langchain4j.*}.</li>
 * <li>FQCN is <strong>not</strong> a valid lookup key: passing a fully-qualified class name (e.g.
 * {@code com.example.MyService}) silently misses the per-service override, because SmallRye Config's
 * {@code @WithParentName} Map cannot key on dotted strings. Callers must pass the canonical AiService name
 * (the {@code @Named} bean value, or the simple class name).</li>
 * </ol>
 */
public class ParallelToolExecutorResolverConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // global default is serial (= the implicit @WithDefault). Override one service to virtual-threads so we
            // can prove the per-service key actually resolves through the @WithParentName Map. We pick a name that
            // avoids the obvious collisions ('tools', 'ai-service', 'guardrails', 'tracing', 'temperature',
            // 'timeout', 'log-requests', 'log-responses', 'log-requests-curl').
            .overrideRuntimeConfigKey("quarkus.langchain4j.myAssistant.tools.execution", "virtual-threads")
            // Also exercise the worker-pool path on a different service to prove the global-vs-per-service split.
            .overrideRuntimeConfigKey("quarkus.langchain4j.workerSvc.tools.execution", "worker-pool");

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

    @Test
    void defaultModeFallthroughReturnsNull() {
        // No override for "unconfiguredSvc" — must fall through to global default (serial -> null executor).
        Executor resolved = ParallelToolExecutorResolver.resolve("unconfiguredSvc", config);
        assertThat(resolved).as("serial mode resolves to null so the existing serial dispatch path is preserved")
                .isNull();
    }

    @Test
    void perServiceVirtualThreadsKeyIsRead() {
        Executor resolved = ParallelToolExecutorResolver.resolve("myAssistant", config);
        // On Java 17-20 we expect downgrade to serial (= null). On Java 21+ we expect a wrapped executor.
        if (Runtime.version().feature() >= 21) {
            assertThat(resolved)
                    .as("Java 21+: virtual-threads mode must produce a VertxContextAwareExecutor wrapper")
                    .isInstanceOf(VertxContextAwareExecutor.class);
        } else {
            assertThat(resolved)
                    .as("Java <21: virtual-threads mode must downgrade to serial (null)")
                    .isNull();
        }
    }

    @Test
    void perServiceWorkerPoolKeyIsRead() {
        // worker-pool resolution requires the ManagedExecutor CDI bean — quarkus-context-propagation is on the test
        // classpath via the Quarkus test stack, so this should yield a real wrapped executor.
        Executor resolved = ParallelToolExecutorResolver.resolve("workerSvc", config);
        assertThat(resolved)
                .as("worker-pool mode must produce a VertxContextAwareExecutor wrapper around ManagedExecutor")
                .isInstanceOf(VertxContextAwareExecutor.class);
    }

    @Test
    void cacheReturnsSameInstanceOnSecondCall() {
        Executor first = ParallelToolExecutorResolver.resolve("workerSvc", config);
        Executor second = ParallelToolExecutorResolver.resolve("workerSvc", config);
        assertThat(second).as("resolver must cache per-service results to avoid re-allocating wrappers")
                .isSameAs(first);
    }

    /**
     * Regression test for the FQCN lookup bug.
     * <p>
     * Prior to this fix, callers passed {@code aiServiceClass.getName()} (e.g.
     * {@code com.example.MyAssistant}) to the resolver. SmallRye Config's {@code @WithParentName} Map cannot
     * route a dotted key to a {@code NamedAiServiceConfig} entry, so every per-service override was silently
     * missed and the resolver returned the global default. The fix is to pass the canonical AiService name
     * (the {@code @Named} bean value or simple class name); this test pins the new contract by demonstrating
     * that an FQCN-shaped key does <em>not</em> match the {@code myAssistant} override.
     */
    @Test
    void fqcnKeyDoesNotResolvePerServiceOverride() {
        // The FQCN of a hypothetical user AiService that happens to have @Named("myAssistant") on it. Even
        // though the per-service override under "myAssistant" is set to virtual-threads, passing the FQCN
        // must NOT find it — it falls through to the global default (serial -> null).
        Executor resolved = ParallelToolExecutorResolver.resolve(
                "com.example.MyAssistant", config);
        assertThat(resolved)
                .as("FQCN must not match a per-service override; expect global default (serial -> null)")
                .isNull();
    }
}
