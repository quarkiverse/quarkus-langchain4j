package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Declares that reading or writing the chat memory of AI services may block the calling thread, for example because the
 * memory calls a model (context compaction) or a remote store.
 * <p>
 * Streaming AI services then switch to a worker thread to execute tools and build the next request, even when all their
 * tools are non-blocking, instead of doing it on the event loop thread of the streaming model client.
 */
public final class BlockingChatMemoryBuildItem extends MultiBuildItem {

    private final String reason;

    /**
     * @param reason why the chat memory may block, for logs and diagnostics
     */
    public BlockingChatMemoryBuildItem(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
