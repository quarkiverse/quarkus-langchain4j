package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.ArchivedToolOutputs;
import io.quarkiverse.langchain4j.compaction.CompactionException;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkiverse.langchain4j.compaction.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.strategies.StrategyFactory;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import io.smallrye.common.annotation.Experimental;

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
@Experimental("This feature is experimental and the API is subject to change")
public class DefaultCompactionProfiles implements CompactionProfiles {

    /**
     * Name of the default profile in reports and logs.
     */
    public static final String DEFAULT = "default";

    private static final Logger LOG = Logger.getLogger(DefaultCompactionProfiles.class);

    private final CompactionConfig config;
    private final StrategyFactory factory;
    private final ChatMemoryStore store;
    private final TokenCounter tokenCounter;
    private final CompactionInspector inspector;
    private final ToolOutputArchive archive;
    private final Event<CompactionReport> events;
    private final Map<String, CompactingChatMemoryProvider> providers = new ConcurrentHashMap<>();

    @Inject
    public DefaultCompactionProfiles(CompactionConfig config, StrategyFactory factory, ChatMemoryStore store,
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
                        + "', which is not configured (quarkus.langchain4j.compaction.profiles." + profile
                        + ".*); configured profiles: " + names());
            }
        });
        logOwnership(usage);
        warnAboutMissingRetrievalTool(usage);
    }

    private void warnAboutMissingRetrievalTool(CompactionProfileUsage usage) {
        Set<String> withTool = new TreeSet<>();
        for (String service : usage.retrievalToolServices()) {
            withTool.add(usage.profiles().getOrDefault(service, CompactionConfig.DEFAULT_PROFILE));
        }
        Set<String> all = new TreeSet<>(config.profiles().keySet());
        all.add(CompactionConfig.DEFAULT_PROFILE);
        for (String profile : all) {
            if (config.profile(profile).toolOutputPruning()
                    .retrieval() == CompactionConfig.Profile.ToolOutputPruning.Retrieval.TOOL
                    && !withTool.contains(profile)) {
                String name = CompactionConfig.DEFAULT_PROFILE.equals(profile) ? DEFAULT : profile;
                LOG.warnf("Compaction profile %s sets tool-output-pruning.retrieval=tool, but none of its AI services"
                        + " lists %s in its tools: the model will be told to call a tool it does not have",
                        name, ArchivedToolOutputs.class.getSimpleName());
            }
        }
    }

    private void logOwnership(CompactionProfileUsage usage) {
        if (!usage.managesChatMemory()) {
            return;
        }
        LOG.infof("Context compaction provides the chat memory of the AI services (profiles: %s): the full history is"
                + " stored in %s and the model receives a compacted view", profileList(), store.getClass().getName());
        Set<String> ignored = new TreeSet<>();
        for (ConfigSource source : ConfigProvider.getConfig().getConfigSources()) {
            // only what the application sets (application.properties, environment, system properties...), not defaults
            if (source.getOrdinal() <= 100 || source.getName().contains("BuildTime")) {
                continue;
            }
            for (String name : source.getPropertyNames()) {
                if (name.startsWith("quarkus.langchain4j.chat-memory.")) {
                    ignored.add(name);
                }
            }
        }
        if (!ignored.isEmpty()) {
            LOG.warnf("%s ignored: context compaction provides the chat memory of the AI services. Use the"
                    + " quarkus.langchain4j.compaction.* properties (budget, strategy, retention) instead",
                    String.join(", ", ignored));
        }
    }

    private String profileList() {
        Set<String> names = new TreeSet<>(names());
        names.add(DEFAULT);
        return String.join(", ", names);
    }

    /**
     * @return the names of the configured profiles, without the default one
     */
    @Override
    public Set<String> names() {
        return new TreeSet<>(config.profiles().keySet());
    }

    /**
     * @param profile the profile name, or {@link CompactionConfig#DEFAULT_PROFILE}
     * @return the chat memory provider of the profile
     * @throws CompactionException if the profile is not configured or its strategy is unknown
     */
    @Override
    public CompactingChatMemoryProvider provider(String profile) {
        if (!config.hasProfile(profile)) {
            throw new CompactionException("Unknown compaction profile '" + profile + "'");
        }
        return providers.computeIfAbsent(profile, this::create);
    }

    /**
     * @return the chat memory provider of the default profile
     */
    @Override
    public CompactingChatMemoryProvider defaultProvider() {
        return provider(CompactionConfig.DEFAULT_PROFILE);
    }

    private ConversationStates states(CompactionConfig.Profile p) {
        return new ConversationStates(p.state().maxConversations(), p.state().idleTimeout(), Clock.systemUTC(),
                memoryId -> {
                    // the conversation is no longer active: release what is attached to it
                    inspector.remove(memoryId);
                    archive.clear(memoryId);
                });
    }

    private static boolean snapshots(CompactionConfig.Profile p) {
        return p.inspectorSnapshots().orElse(LaunchMode.current().isDevOrTest());
    }

    private CompactingChatMemoryProvider create(String profile) {
        CompactionConfig.Profile p = config.profile(profile);
        String name = CompactionConfig.DEFAULT_PROFILE.equals(profile) ? DEFAULT : profile;
        String prefix = CompactionConfig.DEFAULT_PROFILE.equals(profile) ? "quarkus.langchain4j.compaction"
                : "quarkus.langchain4j.compaction.profiles." + profile;
        CompactionConfig.Profile.Budget b = p.budget();
        TokenBudget budget = new TokenBudget(b.contextWindow(), b.reservedCompletionTokens(), b.triggerRatio(),
                b.targetRatio());
        try {
            CompactingChatMemory.Components components = new CompactingChatMemory.Components(name, store,
                    p.strategy(), factory.create(p.strategy(), p), budget, tokenCounter, p.enabled(),
                    p.verifyInvariants(), states(p), inspector, snapshots(p), archive, events::fire,
                    Clock.systemUTC(), new Retention(p.retention().maxMessages(), p.retention().maxAge()));
            return new CompactingChatMemoryProvider(components);
        } catch (CompactionException | IllegalArgumentException e) {
            throw new CompactionException(e.getMessage() + " in compaction profile " + name + " (" + prefix + ".*)", e);
        }
    }
}
