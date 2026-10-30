package io.quarkiverse.langchain4j.jinfer.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")
public interface LangChain4jJinferConfig {

    /**
     * Default model config.
     */
    @WithParentName
    JinferConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, JinferConfig> namedConfig();

    interface JinferConfig {

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to jinfer.
         * Set to {@code false} to disable all models (they will throw when used).
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings.
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings.
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Scoring (rerank) model related settings.
         */
        ScoringModelConfig scoringModel();

        /**
         * Audio transcription model related settings.
         */
        TranscriptionModelConfig transcriptionModel();

        /**
         * Text-to-speech model related settings.
         */
        TtsModelConfig ttsModel();
    }
}
