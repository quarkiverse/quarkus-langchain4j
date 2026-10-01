package io.quarkiverse.langchain4j.typesafe.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.typesafe")
public interface LangChain4jTypeSafeBuildConfig {

    /**
     * Decision model related settings.
     */
    DecisionModelBuildConfig decisionModel();
}
