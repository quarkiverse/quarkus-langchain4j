package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.azure-openai")
public interface Langchain4jAzureOpenAiConfig {

    /**
     * Default model config.
     */
    @WithParentName
    AzureAiConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, AzureAiConfig> namedConfig();

    @ConfigGroup
    interface AzureAiConfig {
        /**
         * The name of your Azure OpenAI Resource. You're required to first deploy a model before you can make calls.
         * <p>
         * This and {@code quarkus.langchain4j.azure-openai.deployment-name} are required if
         * {@code quarkus.langchain4j.azure-openai.endpoint} is not set.
         * If {@code quarkus.langchain4j.azure-openai.endpoint} is not set then this is never read.
         * </p>
         */
        Optional<String> resourceName();

        /**
         * The name of your model deployment. You're required to first deploy a model before you can make calls.
         * <p>
         * This and {@code quarkus.langchain4j.azure-openai.resource-name} are required if
         * {@code quarkus.langchain4j.azure-openai.endpoint} is not set.
         * If {@code quarkus.langchain4j.azure-openai.endpoint} is not set then this is never read.
         * </p>
         */
        Optional<String> deploymentName();

        /**
         * The endpoint for the Azure OpenAI resource.
         * <p>
         * If not specified, then {@code quarkus.langchain4j.azure-openai.resource-name} and
         * {@code quarkus.langchain4j.azure-openai.deployment-name} are required.
         * In this case the endpoint will be set to
         * {@code https://${quarkus.langchain4j.azure-openai.resource-name}.openai.azure.com/openai/deployments/${quarkus.langchain4j.azure-openai.deployment-name}}
         * </p>
         */
        Optional<String> endpoint();

        /**
         * The API version to use for this operation. This follows the YYYY-MM-DD format
         */
        @WithDefault("2023-05-15")
        String apiVersion();

        /**
         * Azure OpenAI API key
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it..
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
        @ConfigDocDefault("false")
        Optional<Boolean> logRequests();

        /**
         * Whether the OpenAI client should log responses
         */
        @ConfigDocDefault("false")
        Optional<Boolean> logResponses();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelConfig embeddingModel();
    }
}
