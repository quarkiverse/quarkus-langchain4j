package io.quarkiverse.langchain4j.compaction.runtime;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;

/**
 * The chat memory provider of a compaction profile: every conversation gets a {@link CompactingChatMemory}.
 * <p>
 * It replaces the default Quarkus LangChain4j {@link ChatMemoryProvider}; each AI service gets the provider of its
 * profile (see {@link CompactionProfile} and {@link ChatMemoryProviderProducer}).
 */
public class CompactingChatMemoryProvider implements ChatMemoryProvider {

    private final CompactingChatMemory.Components components;

    public CompactingChatMemoryProvider(CompactingChatMemory.Components components) {
        this.components = components;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return new CompactingChatMemory(memoryId, components);
    }

    public CompactingChatMemory.Components components() {
        return components;
    }
}
