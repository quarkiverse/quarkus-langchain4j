package io.quarkiverse.langchain4j.runtime.config;

import java.util.OptionalInt;

import io.smallrye.config.WithDefault;

public interface ToolsConfig {

    /**
     * Controls how a batch of tool invocations emitted by a streaming AI service is dispatched.
     * <ul>
     * <li>{@code auto} (default): honor per-tool execution model detected at build time. When every tool
     * in a batch resolves to {@code VIRTUAL_THREAD} the batch is dispatched directly onto a virtual
     * thread, releasing the calling event-loop/worker thread immediately. Otherwise the historical
     * per-tool scheduling behavior is kept.</li>
     * <li>{@code legacy}: disable the batch-level virtual-thread optimization and keep the historical
     * per-tool scheduling behavior.</li>
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
     * virtual-thread-eligible and other tools and the extension skips the
     * full-batch virtual-thread optimization, falling back to the historical per-tool scheduling
     * behavior. The warning includes the AI service method plus the requested and non-virtual
     * tool names. Set to {@code off} to silence entirely.
     */
    @WithDefault("warn")
    MixedBatchLogLevel mixedBatchLogLevel();

    interface VirtualThreadConfig {

        /**
         * Maximum number of tool batches that can be in flight concurrently when using the
         * batch-level virtual-thread dispatch path. This applies only to full
         * {@code VIRTUAL_THREAD} batches in {@code auto} mode. The permit is held for the batch
         * until tool execution finishes and the synchronous handoff to the next model call
         * returns. When unset, dispatch is unbounded (the default). A value of 0 or a negative
         * value is treated as unbounded.
         */
        OptionalInt maxConcurrent();
    }

    enum DispatchMode {
        AUTO,
        LEGACY
    }

    enum MixedBatchLogLevel {
        WARN,
        INFO,
        DEBUG,
        OFF
    }
}
