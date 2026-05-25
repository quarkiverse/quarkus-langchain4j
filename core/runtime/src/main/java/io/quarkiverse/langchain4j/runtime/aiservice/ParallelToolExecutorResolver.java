package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.runtime.config.AiServiceToolsConfig;
import io.quarkiverse.langchain4j.runtime.config.AiServiceToolsExecutionConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkiverse.langchain4j.runtime.config.ToolsExecutionConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;

/**
 * Resolves the parallel-tool executor for each AiService at runtime.
 * <p>
 * Reads the runtime tool-execution config, applies the build-time fallback rules (Java 17-20 +
 * {@code virtual-threads} mode -> serial + warn-once), and returns either a fully-decorated {@link Executor}
 * (already wrapped in {@link VertxContextAwareExecutor}, and bounded for the VT mode) or {@code null} when the
 * resolved mode is {@code serial}.
 * <p>
 * This is an Arc-owned {@code @Singleton}, so all cached executor wrappers and warning state are tied to the
 * current application generation. In dev mode the bean is discarded along with the Arc container when the app
 * hot-reloads, which prevents a stale virtual-thread executor (whose underlying {@code ThreadPerTaskExecutor}
 * was shut down by {@code VirtualThreadsRecorder}'s dev-mode shutdown task) from being reused for the next
 * generation's tool dispatch.
 * <p>
 * {@code @Unremovable} is required because the resolver is obtained via programmatic
 * {@link Arc#container()} lookups from the recorder and the programmatic AiService factory, not through a
 * regular injection point that Arc can use for removal analysis.
 */
@Singleton
@Unremovable
public class ParallelToolExecutorResolver {

    private static final Logger LOG = Logger.getLogger(ParallelToolExecutorResolver.class);

    /** Sentinel used so {@link ConcurrentHashMap} can store the "serial / null executor" decision. */
    private static final Executor NULL_SENTINEL = (Runnable r) -> {
        throw new UnsupportedOperationException(
                "Sentinel executor must not be invoked. The serial mode skips parallel dispatch entirely.");
    };

    private final LangChain4jConfig config;

    /** Cached results per (aiServiceName -> Executor). {@code null} means serial. */
    private final Map<String, Executor> cache = new ConcurrentHashMap<>();

    /** Tracks AiService names for which we've already emitted the Java-version downgrade warning. */
    private final Set<String> warnedDowngrades = ConcurrentHashMap.newKeySet();

    /** Lazily-initialised shared VT executor wrapper (one per application generation). */
    private volatile Executor sharedVirtualThreadExecutor;

    @Inject
    public ParallelToolExecutorResolver(LangChain4jConfig config) {
        this.config = config;
    }

    /**
     * Resolve the parallel executor for a given AiService.
     *
     * @param aiServiceName the AiService's canonical name — the same key used under
     *        {@code quarkus.langchain4j.<ai-service-name>.*}. For declarative AiServices this is the
     *        {@code @Named} bean name when present on the {@code @RegisterAiService} interface, otherwise the
     *        simple class name. The resolver does <strong>not</strong> accept a fully-qualified class name; passing
     *        a FQCN will silently miss every per-service override because SmallRye Config's
     *        {@code @WithParentName} Map cannot key on dotted strings.
     * @return the executor to pass to {@code AiServices.executeToolsConcurrently(Executor)} / equivalent, or
     *         {@code null} if the mode resolves to serial (callers must keep the existing serial dispatch path
     *         when this returns {@code null}).
     */
    public Executor resolve(String aiServiceName) {
        Executor cached = cache.get(aiServiceName);
        if (cached != null) {
            return cached == NULL_SENTINEL ? null : cached;
        }
        Executor resolved = doResolve(aiServiceName);
        cache.put(aiServiceName, resolved == null ? NULL_SENTINEL : resolved);
        return resolved;
    }

    private Executor doResolve(String aiServiceName) {
        ToolsExecutionMode requested = effectiveMode(aiServiceName);
        ToolsExecutionMode resolved = applyJavaVersionFallback(aiServiceName, requested);
        switch (resolved) {
            case SERIAL:
                return null;
            case VIRTUAL_THREADS:
                return sharedVirtualThreadExecutor(config.tools().virtualThreadsMaxConcurrency());
            case WORKER_POOL:
                return workerPoolExecutor();
            default:
                throw new IllegalStateException("Unknown ToolsExecutionMode: " + resolved);
        }
    }

    private ToolsExecutionMode effectiveMode(String aiServiceName) {
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

    private ToolsExecutionMode applyJavaVersionFallback(String aiServiceName, ToolsExecutionMode requested) {
        if (requested == ToolsExecutionMode.VIRTUAL_THREADS && !isJava21OrLater()) {
            if (warnedDowngrades.add(aiServiceName == null ? "" : aiServiceName)) {
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

    private Executor sharedVirtualThreadExecutor(int maxConcurrency) {
        Executor cached = sharedVirtualThreadExecutor;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (sharedVirtualThreadExecutor != null) {
                return sharedVirtualThreadExecutor;
            }
            // Quarkus' VirtualThreadsRecorder.getCurrent() returns an unbounded VT-per-task executor on Java 21+.
            // We must still apply max-concurrency as a Semaphore wrapper, then wrap in VertxContextAwareExecutor so
            // the captured caller context is re-installed inside each VT task before downstream code runs.
            Executor vt = VirtualThreadsRecorder.getCurrent();
            Executor bounded = new BoundedExecutor(vt, maxConcurrency);
            sharedVirtualThreadExecutor = new VertxContextAwareExecutor(bounded);
            return sharedVirtualThreadExecutor;
        }
    }

    private Executor workerPoolExecutor() {
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

    /** Snapshot of warned-downgrade keys, for diagnostics in tests. */
    Set<String> warnedDowngradesSnapshot() {
        return new HashSet<>(warnedDowngrades);
    }

    /**
     * Shared lookup-and-resolve helper used by both AiService construction call sites
     * ({@code QuarkusAiServicesFactory.QuarkusAiServices.build()} and
     * {@code AiServicesRecorder.createDeclarativeAiService}).
     * <p>
     * Looks the resolver up through Arc so the cache state is tied to the current application generation, and
     * applies the environment-driven fallback semantics: {@link UnsupportedOperationException} (and only that)
     * is the conventional Java signal for "feature exists but is not usable in this JVM/runtime" — log INFO and
     * return {@code null} so the application keeps working on serial dispatch. Every other {@link RuntimeException}
     * (invalid concurrency, unknown mode, malformed config mapping, etc.) propagates so misconfiguration fails
     * loudly at startup with a clear message.
     *
     * @param aiServiceName the canonical AiService name used as the resolver key
     * @param log the caller's logger (kept on each call site so the log line identifies which path triggered the fallback)
     * @return the parallel executor, or {@code null} when serial dispatch should be used
     */
    public static Executor resolveFromArc(String aiServiceName, Logger log) {
        try {
            InstanceHandle<ParallelToolExecutorResolver> handle = Arc.container()
                    .instance(ParallelToolExecutorResolver.class);
            if (!handle.isAvailable()) {
                log.warnf(
                        "Parallel-tool executor resolver is not available; AiService '%s' will use serial tool dispatch.",
                        aiServiceName);
                return null;
            }
            return handle.get().resolve(aiServiceName);
        } catch (UnsupportedOperationException e) {
            log.infof(e,
                    "Parallel-tool executor unavailable in this environment for AiService '%s'; falling back to serial dispatch.",
                    aiServiceName);
            return null;
        }
    }
}
