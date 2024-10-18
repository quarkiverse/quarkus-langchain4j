package io.quarkiverse.langchain4j.cohere;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.cohere")
public interface LangChain4jCohereBuildConfig {

    /**
     * Scoring model related settings.
     */
    ScoringModelBuildConfig scoringModel();
}
