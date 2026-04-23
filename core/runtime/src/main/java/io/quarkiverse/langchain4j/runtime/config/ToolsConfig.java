package io.quarkiverse.langchain4j.runtime.config;

import java.util.OptionalInt;

import io.smallrye.config.WithDefault;

public interface ToolsConfig {

    /**
     * Controls how a batch of tool invocations emitted by a streaming AI service is dispatched.
     * <ul>
     * <li>{@code auto} (default): honor per-tool execution model detected at build time. When every tool
     * in a batch is annotated {@code @RunOnVirtualThread} the batch is dispatched directly onto a virtual
     * thread, releasing the calling event-loop/worker thread immediately. Otherwise the current
     * worker-thread behavior is kept.</li>
     * <li>{@code virtual-thread}: always dispatch tool batches onto a virtual thread, regardless of
     * per-tool annotations.</li>
     * <li>{@code worker}: always dispatch tool batches onto a worker thread (historical behavior).</li>
     * </ul>
     */
    @WithDefault("auto")
    DispatchMode dispatch();

    /**
     * Virtual-thread specific tuning.
     */
    VirtualThreadConfig virtualThread();

    /**
     * Log level at which a warning is emitted when a tool batch contains a mix of
     * {@code @RunOnVirtualThread} and blocking/non-blocking tools and the extension falls back to
     * worker-thread dispatch for the whole batch. Set to {@code off} to silence entirely.
     */
    @WithDefault("warn")
    MixedBatchLogLevel mixedBatchLogLevel();

    interface VirtualThreadConfig {

        /**
         * Maximum number of tool batches that can be in flight concurrently on virtual threads. When
         * unset, dispatch is unbounded (the default). A value of 0 or a negative value is treated as
         * unbounded.
         */
        OptionalInt maxConcurrent();
    }

    enum DispatchMode {
        AUTO,
        VIRTUAL_THREAD,
        WORKER
    }

    enum MixedBatchLogLevel {
        WARN,
        INFO,
        DEBUG,
        OFF
    }
}
