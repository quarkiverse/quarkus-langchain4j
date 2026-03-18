package io.quarkiverse.langchain4j.chatscopes;

import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeMemoryImpl;

public interface ChatScopeMemory {

    /**
     * Clears chat memory for chat scoped beans in the current chat scope only.
     */
    static void clearMemory() {
        ChatScopeMemoryImpl.clearMemory();
    }

    /**
     * Schedule a wipe of chat memory for chat scoped beans in the current chat scope
     * Wipe will be performed just before the CDI request context is terminated.
     */
    static void scheduleWipe() {
        ChatScopeMemoryImpl.scheduleWipe();
    }

    /**
     * Abort a scheduled wipe of chat memory for chat scoped beans in the current chat scope.
     * An abort cannot be canceled.
     */
    static void abortWipe() {
        ChatScopeMemoryImpl.abortWipe();
    }

    /**
     * Checks if a wipe of chat memory is scheduled for chat scoped beans in the current chat scope.
     *
     * @return true if a wipe is scheduled, false otherwise
     */
    static boolean wipeScheduled() {
        return ChatScopeMemoryImpl.wipeScheduled();
    }

}
