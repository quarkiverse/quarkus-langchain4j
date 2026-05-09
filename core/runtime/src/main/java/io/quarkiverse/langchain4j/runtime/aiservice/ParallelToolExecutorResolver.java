package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.runtime.config.AiServiceToolsConfig;
import io.quarkiverse.langchain4j.runtime.config.AiServiceToolsExecutionConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkiverse.langchain4j.runtime.config.ToolsExecutionConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;

/**
 * Resolves the parallel-tool executor for each AiService at runtime.
 * <p>
 * This is the Phase 0 plumbing surface: it reads the runtime tool-execution config, applies the build-time fallback
 * rules (Java 17-20 + {@code virtual-threads} mode -> serial + warn-once), and returns either a fully-decorated
 * {@link Executor} (already wrapped in {@link VertxContextAwareExecutor}, and bounded for the VT mode) or {@code null}
 * when the resolved mode is {@code serial}.
 * <p>
 * Phase 1 will populate {@code QuarkusAiServiceContext.parallelToolExecutor} from this resolver. Phase 0 only lays
 * the plumbing.
 */
public final class ParallelToolExecutorResolver {

    private static final Logger LOG = Logger.getLogger(ParallelToolExecutorResolver.class);

    /** Cached results per (aiServiceName -> Executor). {@code null} means serial. */
    private static final Map<String, Executor> CACHE = new ConcurrentHashMap<>();

    /** Sentinel used so {@link ConcurrentHashMap} can store the "serial / null executor" decision. */
    private static final Executor NULL_SENTINEL = (Runnable r) -> {
        throw new UnsupportedOperationException(
                "Sentinel executor must not be invoked. The serial mode skips parallel dispatch entirely.");
    };

    /** Tracks AiService names for which we've already emitted the Java-version downgrade warning. */
    private static final Set<String> WARNED_DOWNGRADES = ConcurrentHashMap.newKeySet();

    /** Lazily-initialised shared VT executor wrapper (one per JVM instance). */
    private static volatile Executor SHARED_VT_EXECUTOR;

    private ParallelToolExecutorResolver() {
        // utility
    }

    /**
     * Resolve the parallel executor for a given AiService.
     *
     * @param aiServiceName the AiService's declared name (typically the {@code RegisterAiService} value or simple
     *        class name)
     * @param config the runtime LangChain4j config
     * @return the executor to pass to {@code AiServices.executeToolsConcurrently(Executor)} / equivalent, or
     *         {@code null} if the mode resolves to serial (Phase 1 callers must keep the existing serial dispatch
     *         path when this returns {@code null}).
     */
    public static Executor resolve(String aiServiceName, LangChain4jConfig config) {
        Executor cached = CACHE.get(aiServiceName);
        if (cached != null) {
            return cached == NULL_SENTINEL ? null : cached;
        }
        Executor resolved = doResolve(aiServiceName, config);
        CACHE.put(aiServiceName, resolved == null ? NULL_SENTINEL : resolved);
        return resolved;
    }

    /**
     * Test helper — clears the per-JVM resolver caches so unit tests can re-resolve under different config.
     */
    public static void resetForTesting() {
        CACHE.clear();
        WARNED_DOWNGRADES.clear();
        SHARED_VT_EXECUTOR = null;
    }

    private static Executor doResolve(String aiServiceName, LangChain4jConfig config) {
        ToolsExecutionMode requested = effectiveMode(aiServiceName, config);
        ToolsExecutionMode resolved = applyJavaVersionFallback(aiServiceName, requested);
        switch (resolved) {
            case SERIAL:
                return null;
            case VIRTUAL_THREADS:
                return sharedVirtualThreadExecutor(config.tools().execution().virtualThreadsMaxConcurrency());
            case WORKER_POOL:
                return workerPoolExecutor();
            default:
                throw new IllegalStateException("Unknown ToolsExecutionMode: " + resolved);
        }
    }

    private static ToolsExecutionMode effectiveMode(String aiServiceName, LangChain4jConfig config) {
        ToolsExecutionConfig globalCfg = config.tools().execution();
        Map<String, LangChain4jConfig.NamedAiServiceConfig> named = config.namedAiServices();
        if (named != null && aiServiceName != null) {
            LangChain4jConfig.NamedAiServiceConfig perService = named.get(aiServiceName);
            if (perService != null) {
                AiServiceToolsConfig perServiceTools = perService.tools();
                if (perServiceTools != null) {
                    AiServiceToolsExecutionConfig perServiceExec = perServiceTools.execution();
                    if (perServiceExec != null) {
                        Optional<ToolsExecutionMode> override = perServiceExec.mode();
                        if (override.isPresent()) {
                            return override.get();
                        }
                    }
                }
            }
        }
        return globalCfg.mode();
    }

    private static ToolsExecutionMode applyJavaVersionFallback(String aiServiceName, ToolsExecutionMode requested) {
        if (requested == ToolsExecutionMode.VIRTUAL_THREADS && !isJava21OrLater()) {
            if (WARNED_DOWNGRADES.add(aiServiceName == null ? "" : aiServiceName)) {
                LOG.warnf(
                        "AiService '%s' was configured with tools.execution=virtual-threads but the JVM is Java %d. "
                                + "Falling back to serial tool dispatch. Run on Java 21+ or set "
                                + "quarkus.langchain4j.tools.execution=worker-pool to keep parallel execution.",
                        aiServiceName, Runtime.version().feature());
            }
            return ToolsExecutionMode.SERIAL;
        }
        return requested;
    }

    /**
     * Java version detection — uses {@link Runtime.Version#feature()} which has been available since Java 9.
     * Crucially, this avoids any direct Java 21 API ({@code Thread.ofVirtual()}, {@code Thread#isVirtual},
     * {@code Executors#newVirtualThreadPerTaskExecutor}); the extension still compiles against Java 17 release.
     */
    static boolean isJava21OrLater() {
        return Runtime.version().feature() >= 21;
    }

    private static Executor sharedVirtualThreadExecutor(int maxConcurrency) {
        Executor cached = SHARED_VT_EXECUTOR;
        if (cached != null) {
            return cached;
        }
        synchronized (ParallelToolExecutorResolver.class) {
            if (SHARED_VT_EXECUTOR != null) {
                return SHARED_VT_EXECUTOR;
            }
            // Quarkus' VirtualThreadsRecorder.getCurrent() returns an unbounded VT-per-task executor on Java 21+.
            // We must still apply max-concurrency as a Semaphore wrapper, then wrap in VertxContextAwareExecutor so
            // the captured caller context is re-installed inside each VT task before downstream code runs.
            Executor vt = VirtualThreadsRecorder.getCurrent();
            Executor bounded = new BoundedExecutor(vt, maxConcurrency);
            SHARED_VT_EXECUTOR = new VertxContextAwareExecutor(bounded);
            return SHARED_VT_EXECUTOR;
        }
    }

    private static Executor workerPoolExecutor() {
        InstanceHandle<ManagedExecutor> handle = Arc.container().instance(ManagedExecutor.class);
        if (!handle.isAvailable()) {
            LOG.warn("ManagedExecutor CDI bean not available; tools.execution=worker-pool falling back to serial. "
                    + "Add the quarkus-context-propagation extension to enable worker-pool mode.");
            return null;
        }
        return new VertxContextAwareExecutor(handle.get());
    }

    /**
     * Helper used by tests to build a wrapper around an arbitrary delegate without touching CDI.
     */
    public static Executor wrapForTesting(Executor delegate) {
        return new VertxContextAwareExecutor(delegate);
    }

    /** Reset only the warned-downgrades set without flushing the executor cache. Internal helper. */
    static void clearDowngradeWarnings() {
        WARNED_DOWNGRADES.clear();
    }

    /** Used by build-time recorder to expose just the warning logic when scanning at startup. */
    static Set<String> warnedDowngradesSnapshot() {
        return new HashSet<>(WARNED_DOWNGRADES);
    }
}
