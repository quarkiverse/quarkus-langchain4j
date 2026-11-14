package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Models used by the compaction strategies. Fixed at build time, because Quarkus LangChain4j creates model beans for
 * the names requested during the build.
 */
@ConfigMapping(prefix = "quarkus.langchain4j.compaction")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface CompactionModelsConfig {

    /**
     * Summarization.
     */
    Summary summary();

    /**
     * Relevance scoring.
     */
    Relevance relevance();

    interface Summary {
        /**
         * Name of the chat model used to summarize ({@code quarkus.langchain4j.<provider>.<name>.*}). Uses the default
         * model if not set. Pointing this to a cheaper model is recommended.
         */
        Optional<String> modelName();
    }

    interface Relevance {
        /**
         * Name of the embedding model used to score relevance. When no embedding model is available, a lexical
         * similarity is used.
         */
        Optional<String> embeddingModelName();
    }
}
