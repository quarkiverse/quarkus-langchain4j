package io.quarkiverse.langchain4j.compaction.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Whether the extension provides the chat memory provider of the application, or steps aside because the application
 * declares its own.
 */
final class ChatMemoryOwnershipBuildItem extends SimpleBuildItem {

    private final boolean managesChatMemory;

    ChatMemoryOwnershipBuildItem(boolean managesChatMemory) {
        this.managesChatMemory = managesChatMemory;
    }

    boolean managesChatMemory() {
        return managesChatMemory;
    }
}
