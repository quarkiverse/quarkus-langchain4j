package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * A no-op implementation of {@link StreamingHandle} used as a default before
 * the actual handle is captured from the streaming provider.
 * <p>
 * This handles the edge case where cancellation is triggered before any partial
 * response arrives (and thus before the real handle is available).
 */
class NoopStreamingHandle implements StreamingHandle {

    static final NoopStreamingHandle INSTANCE = new NoopStreamingHandle();

    private NoopStreamingHandle() {
    }

    @Override
    public void cancel() {
        // No-op: stream not yet started or provider doesn't support cancellation
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
