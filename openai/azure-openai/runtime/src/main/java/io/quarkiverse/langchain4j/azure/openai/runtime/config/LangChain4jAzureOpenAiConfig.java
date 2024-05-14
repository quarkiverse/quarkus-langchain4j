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
public interface LangChain4jAzureOpenAiConfig {

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
         * The Azure AD token to use for this operation.
         * If present, then the requests towards OpenAI will include this in the Authorization header.
         * Note that this property overrides the functionality of {@code quarkus.langchain4j.azure-openai.api-key}.
         */
        Optional<String> adToken();

        /**
         * The API version to use for this operation. This follows the YYYY-MM-DD format
         */
        @WithDefault("2023-05-15")
        String apiVersion();

        /**
         * Azure OpenAI API key
         */
        Optional<String> apiKey();

        /**
         * Timeout for OpenAI calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * The maximum number of times to retry. 1 means exactly one attempt, with retrying disabled.
         *
         * @deprecated Using the fault tolerance mechanisms built in Langchain4j is not recommended. If possible,
         *             use MicroProfile Fault Tolerance instead.
         */
        @WithDefault("1")
        Integer maxRetries();

        /**
         * Whether the OpenAI client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the OpenAI client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Image model related settings
         */
        ImageModelConfig imageModel();
    }
}
