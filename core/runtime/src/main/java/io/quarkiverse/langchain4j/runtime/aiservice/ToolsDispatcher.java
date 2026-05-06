package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.Semaphore;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkiverse.langchain4j.runtime.config.ToolsConfig;
import io.quarkus.arc.Unremovable;

/**
 * Holds the resolved tool-dispatch policy: the global dispatch mode, the log level for
 * mixed-batch warnings, and the (optional) shared semaphore that bounds concurrent
 * batches using the batch-level virtual-thread dispatch path.
 *
 * <p>
 * Exposed as a CDI singleton so the semaphore is shared across every
 * {@link QuarkusAiServiceStreamingResponseHandler} instance in the container, while still
 * being a fresh instance per Quarkus application boot (each {@code QuarkusUnitTest} starts
 * its own container).
 * </p>
 */
@Singleton
@Unremovable
public class ToolsDispatcher {

    /**
     * Fallback instance used when CDI is not available (should not happen in practice in a
     * running Quarkus application; kept as a defensive default for bootstrap paths).
     */
    public static final ToolsDispatcher DEFAULT = new ToolsDispatcher();

    private final LangChain4jConfig config;
    private ToolsConfig.DispatchMode dispatchMode;
    private ToolsConfig.MixedBatchLogLevel mixedBatchLogLevel;
    private Semaphore semaphore;
    private boolean parallelVirtualThreadBatch;

    @Inject
    public ToolsDispatcher(LangChain4jConfig config) {
        this.config = config;
    }

    private ToolsDispatcher() {
        this.config = null;
        this.dispatchMode = ToolsConfig.DispatchMode.AUTO;
        this.mixedBatchLogLevel = ToolsConfig.MixedBatchLogLevel.WARN;
        this.semaphore = null;
        this.parallelVirtualThreadBatch = true;
    }

    @PostConstruct
    void init() {
        if (config == null) {
            return;
        }
        ToolsConfig tools = config.tools();
        this.dispatchMode = tools.dispatch();
        this.mixedBatchLogLevel = tools.mixedBatchLogLevel();
        int max = tools.virtualThread().maxConcurrent().orElse(-1);
        this.semaphore = max > 0 ? new Semaphore(max) : null;
        this.parallelVirtualThreadBatch = tools.parallelVirtualThreadBatch();
    }

    public ToolsConfig.DispatchMode dispatchMode() {
        return dispatchMode;
    }

    public ToolsConfig.MixedBatchLogLevel mixedBatchLogLevel() {
        return mixedBatchLogLevel;
    }

    public Semaphore semaphore() {
        return semaphore;
    }

    public boolean parallelVirtualThreadBatch() {
        return parallelVirtualThreadBatch;
    }
}
