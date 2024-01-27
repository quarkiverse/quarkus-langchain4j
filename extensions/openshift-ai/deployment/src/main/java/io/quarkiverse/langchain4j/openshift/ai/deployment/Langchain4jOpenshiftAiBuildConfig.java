package io.quarkiverse.langchain4j.openshift.ai.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.openshift-ai")
public interface Langchain4jOpenshiftAiBuildConfig {

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();
}
