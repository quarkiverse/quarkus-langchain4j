package io.quarkiverse.langchain4j.jinfer.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jinfer")
public interface LangChain4jJinferBuildConfig {

    /**
     * Chat model related settings.
     */
    ChatModelBuildConfig chatModel();

    /**
     * Embedding model related settings.
     */
    EmbeddingModelBuildConfig embeddingModel();

    /**
     * Scoring (rerank) model related settings.
     */
    ScoringModelBuildConfig scoringModel();

    /**
     * Audio transcription model related settings.
     */
    TranscriptionModelBuildConfig transcriptionModel();

    /**
     * Text-to-speech model related settings.
     */
    TtsModelBuildConfig ttsModel();
}
