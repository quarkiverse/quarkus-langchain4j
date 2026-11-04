package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

/**
 * Context compaction configuration.
 */
@ConfigMapping(prefix = "quarkus.langchain4j.compaction")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface CompactionConfig {

    /**
     * Name of the profile configured directly under {@code quarkus.langchain4j.compaction}, used by AI services without
     * {@link CompactionProfile}.
     */
    String DEFAULT_PROFILE = "<default>";

    /**
     * Compaction profiles. The default profile is configured directly under {@code quarkus.langchain4j.compaction}, named
     * profiles under {@code quarkus.langchain4j.compaction."<name>"}. A named profile does not inherit from the default
     * one: unset properties take their built-in default.
     */
    @ConfigDocMapKey("profile-name")
    @WithParentName
    @WithUnnamedKey(DEFAULT_PROFILE)
    @WithDefaults
    Map<String, Profile> profiles();

    /**
     * @return the default profile
     */
    default Profile defaultProfile() {
        return profiles().get(DEFAULT_PROFILE);
    }

    /**
     * @return {@code true} if the profile is the default one or has at least one property set
     */
    default boolean hasProfile(String name) {
        return DEFAULT_PROFILE.equals(name) || profiles().containsKey(name);
    }

    interface Profile {

        /**
         * Whether compaction is enabled. When disabled, chat memories store and send the full history.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Identifier of the strategy applied to the conversations of the AI services using this profile.
         */
        @WithDefault("pipeline")
        String strategy();

        /**
         * Whether the compacted view is checked against the compaction invariants. A violation falls back to the token
         * window strategy and is reported.
         */
        @WithDefault("true")
        boolean verifyInvariants();

        /**
         * Token budget.
         */
        Budget budget();

        /**
         * Message window strategy ({@code message-window}).
         */
        MessageWindow messageWindow();

        /**
         * Tool output pruning strategy ({@code tool-output-pruning}).
         */
        ToolOutputPruning toolOutputPruning();

        /**
         * Rolling summary and anchored summary strategies ({@code rolling-summary}, {@code anchored-summary}).
         */
        Summary summary();

        /**
         * Time decay strategy ({@code time-decay}).
         */
        TimeDecay timeDecay();

        /**
         * Relevance strategy ({@code relevance}).
         */
        Relevance relevance();

        /**
         * Pipeline strategy ({@code pipeline}).
         */
        Pipeline pipeline();

        interface Budget {
            /**
             * Context window of the target model, in tokens.
             */
            @WithDefault("16000")
            int contextWindow();

            /**
             * Tokens reserved for the completion.
             */
            @WithDefault("1024")
            int reservedCompletionTokens();

            /**
             * Fraction of the usable window above which compaction runs.
             */
            @WithDefault("0.8")
            double triggerRatio();

            /**
             * Fraction of the usable window strategies aim for.
             */
            @WithDefault("0.6")
            double targetRatio();
        }

        interface MessageWindow {
            /**
             * Maximum number of non-system messages kept.
             */
            @WithDefault("20")
            int maxMessages();
        }

        interface ToolOutputPruning {
            /**
             * Number of most recent turns whose tool outputs are never pruned.
             */
            @WithDefault("2")
            int keepLastTurns();

            /**
             * Tool outputs shorter than this number of characters are kept.
             */
            @WithDefault("200")
            int minChars();

            /**
             * Number of characters of the original output kept as a preview in the stub.
             */
            @WithDefault("120")
            int previewChars();
        }

        interface Summary {
            /**
             * Number of most recent turns kept verbatim.
             */
            @WithDefault("4")
            int keepLastTurns();

            /**
             * Number of first turns kept verbatim by the anchored summary strategy (the original goal).
             */
            @WithDefault("1")
            int anchorTurns();

            /**
             * Soft limit for the summary length, in words.
             */
            @WithDefault("250")
            int maxWords();
        }

        interface TimeDecay {
            /**
             * Turns younger than this are kept verbatim.
             */
            @WithDefault("15m")
            Duration recent();

            /**
             * Turns older than this are summarized (or dropped if summarization fails). Turns between {@code recent} and
             * {@code stale} have their tool outputs pruned and long messages truncated.
             */
            @WithDefault("2h")
            Duration stale();

            /**
             * Messages of aging turns are truncated to this number of characters.
             */
            @WithDefault("400")
            int truncateChars();
        }

        interface Relevance {
            /**
             * Number of most recent turns always kept.
             */
            @WithDefault("3")
            int keepLastTurns();

            /**
             * Number of older turns kept, chosen by relevance to the latest user message.
             */
            @WithDefault("4")
            int topK();
        }

        interface Pipeline {
            /**
             * Identifiers of the strategies applied in order. The pipeline stops as soon as the history fits the target.
             */
            @WithDefault("tool-output-pruning,duplicate-collapse,rolling-summary,token-window")
            List<String> steps();
        }
    }
}
