package io.quarkiverse.langchain4j.compaction;

import java.util.Set;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.smallrye.common.annotation.Experimental;

/**
 * The compaction profiles of the application (see {@link CompactionProfile}). Inject it to obtain the chat memory
 * provider of a profile, for example from a custom {@code ChatMemoryProvider} bean or a
 * {@code chatMemoryProviderSupplier}.
 * <p>
 * The default profile is configured directly under {@code quarkus.langchain4j.compaction}, named profiles under
 * {@code quarkus.langchain4j.compaction.profiles."<name>"}. Each profile has its own strategy, budget and conversation
 * state, and all profiles share the chat memory store.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface CompactionProfiles {

    /**
     * @return the names of the configured named profiles, without the default one
     */
    Set<String> names();

    /**
     * @param name a named profile
     * @return the chat memory provider of the profile
     * @throws CompactionException if the profile is not configured
     */
    ChatMemoryProvider provider(String name);

    /**
     * @return the chat memory provider of the default profile
     */
    ChatMemoryProvider defaultProvider();
}
