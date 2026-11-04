package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.CompactionException;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.strategies.StrategyFactory;
import io.quarkus.runtime.Startup;

/**
 * The compaction profiles of the application (see {@link CompactionProfile}): each
 * profile has its own strategy instance, budget and conversation state, and shares the chat memory store, token
 * counter, inspector and archive with the others.
 * <p>
 * Created at startup, so a misconfigured profile, or an AI service referencing a profile that is not configured, fails
 * the application start.
 */
@Startup
@Singleton
public class CompactionProfiles {

    /**
     * Name of the default profile in reports and logs.
     */
    public static final String DEFAULT = "default";

    private final CompactionConfig config;
    private final StrategyFactory factory;
    private final ChatMemoryStore store;
    private final TokenCounter tokenCounter;
    private final CompactionInspector inspector;
    private final ToolOutputArchive archive;
    private final Event<CompactionReport> events;
    private final Map<String, CompactingChatMemoryProvider> providers = new ConcurrentHashMap<>();

    @Inject
    public CompactionProfiles(CompactionConfig config, StrategyFactory factory, ChatMemoryStore store,
            TokenCounter tokenCounter, CompactionInspector inspector, ToolOutputArchive archive,
            Event<CompactionReport> events, CompactionProfileUsage usage) {
        this.config = config;
        this.factory = factory;
        this.store = store;
        this.tokenCounter = tokenCounter;
        this.inspector = inspector;
        this.archive = archive;
        this.events = events;
        provider(CompactionConfig.DEFAULT_PROFILE);
        for (String profile : config.profiles().keySet()) {
            provider(profile);
        }
        usage.profiles().forEach((service, profile) -> {
            if (!config.hasProfile(profile)) {
                throw new CompactionException("AI service " + service + " uses the compaction profile '" + profile
                        + "', which is not configured (quarkus.langchain4j.compaction." + profile
                        + ".*); configured profiles: " + named());
            }
        });
    }

    /**
     * @return the names of the configured profiles, without the default one
     */
    public Set<String> named() {
        Set<String> names = new TreeSet<>(config.profiles().keySet());
        names.remove(CompactionConfig.DEFAULT_PROFILE);
        return names;
    }

    /**
     * @param profile the profile name, or {@link CompactionConfig#DEFAULT_PROFILE}
     * @return the chat memory provider of the profile
     * @throws CompactionException if the profile is not configured or its strategy is unknown
     */
    public CompactingChatMemoryProvider provider(String profile) {
        if (!config.hasProfile(profile)) {
            throw new CompactionException("Unknown compaction profile '" + profile + "'");
        }
        return providers.computeIfAbsent(profile, this::create);
    }

    /**
     * @return the chat memory provider of the default profile
     */
    public CompactingChatMemoryProvider defaultProvider() {
        return provider(CompactionConfig.DEFAULT_PROFILE);
    }

    private CompactingChatMemoryProvider create(String profile) {
        CompactionConfig.Profile p = config.profiles().get(profile);
        String name = CompactionConfig.DEFAULT_PROFILE.equals(profile) ? DEFAULT : profile;
        String prefix = CompactionConfig.DEFAULT_PROFILE.equals(profile) ? "quarkus.langchain4j.compaction"
                : "quarkus.langchain4j.compaction." + profile;
        CompactionConfig.Profile.Budget b = p.budget();
        TokenBudget budget = new TokenBudget(b.contextWindow(), b.reservedCompletionTokens(), b.triggerRatio(),
                b.targetRatio());
        try {
            CompactingChatMemory.Components components = new CompactingChatMemory.Components(name, store,
                    p.strategy(), factory.create(p.strategy(), p), budget, tokenCounter, p.enabled(),
                    p.verifyInvariants(), new ConversationStates(), inspector, archive, events::fire,
                    Clock.systemUTC());
            return new CompactingChatMemoryProvider(components);
        } catch (CompactionException e) {
            throw new CompactionException(e.getMessage() + " in compaction profile " + name + " (" + prefix + ".*)", e);
        }
    }
}
