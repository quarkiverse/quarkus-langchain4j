package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

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

        default Optional<String> endPointFor(EndpointType type) {
            var deepEndpoint = switch (type) {
                case CHAT -> chatModel().endpoint();
                case EMBEDDING -> embeddingModel().endpoint();
                case IMAGE -> imageModel().endpoint();
            };
            return deepEndpoint.or(new Supplier<Optional<String>>() {
                @Override
                public Optional<String> get() {
                    return endpoint();
                }
            });
        }

        default Optional<String> resourceNameFor(EndpointType type) {
            var deepResourceName = switch (type) {
                case CHAT -> chatModel().resourceName();
                case EMBEDDING -> embeddingModel().resourceName();
                case IMAGE -> imageModel().resourceName();
            };
            return deepResourceName.or(new Supplier<Optional<String>>() {
                @Override
                public Optional<String> get() {
                    return resourceName();
                }
            });
        }

        default Optional<String> deploymentNameFor(EndpointType type) {
            var deepDeploymentName = switch (type) {
                case CHAT -> chatModel().deploymentName();
                case EMBEDDING -> embeddingModel().deploymentName();
                case IMAGE -> imageModel().deploymentName();
            };
            return deepDeploymentName.or(new Supplier<Optional<String>>() {
                @Override
                public Optional<String> get() {
                    return deploymentName();
                }
            });
        }

        enum EndpointType {
            CHAT,
            EMBEDDING,
            IMAGE
        }
    }

    /**
     * Whether to use the current security identity's access token to access Azure OpenAI provider.
     * If it is set to {@code true} but the security identity has no access token then either OpenAI key or pre-configured Azure
     * token must be used.
     * Set to {@code false} to access Azure OpenAI provider only with the OpenAI key or pre-configured Azure token.
     */
    @WithDefault("false")
    Boolean useSecurityIdentityToken();
}
