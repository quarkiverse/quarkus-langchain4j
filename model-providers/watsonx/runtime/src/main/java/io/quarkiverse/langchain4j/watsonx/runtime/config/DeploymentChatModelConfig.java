package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface DeploymentChatModelConfig extends FoundationChatModelConfig {

    /**
     * The deployment ID of the model deployed in watsonx.ai.
     * <p>
     * Setting this property routes all chat requests to the deployment chat API.
     */
    Optional<String> deploymentId();
}
