package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.azure-openai")
public interface Langchain4jAzureOpenAiConfig {

    /**
     * The name of your Azure OpenAI Resource
     */
    String resourceName();

    /**
     * The name of your model deployment. You're required to first deploy a model before you can make calls.
     */
    String deploymentId();

    /**
     * The base url for the Azure OpenAI resource. Defaults to
     * {@code https://${quarkus.langchain4j.azure-openai.resource-name}.openai.azure.com/openai/deployments/${quarkus.langchain4j.azure-openai.deployment-id}}.
     */
    @WithDefault("https://${quarkus.langchain4j.azure-openai.resource-name}.openai.azure.com/openai/deployments/${quarkus.langchain4j.azure-openai.deployment-id}")
    String baseUrl();

    /**
     * The API version to use for this operation. This follows the YYYY-MM-DD format
     */
    @WithDefault("2023-05-15")
    String apiVersion();

    /**
     * Azure OpenAI API key
     */
    String apiKey();

    /**
     * Timeout for OpenAI calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * The maximum number of times to retry
     */
    @WithDefault("3")
    Integer maxRetries();

    /**
     * Whether the OpenAI client should log requests
     */
    @WithDefault("false")
    Boolean logRequests();

    /**
     * Whether the OpenAI client should log responses
     */
    @WithDefault("false")
    Boolean logResponses();

    /**
     * Chat model related settings
     */
    ChatModelConfig chatModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelConfig embeddingModel();
}
