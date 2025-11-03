package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {
    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.resource-name}
     * specifically for chat models if it is set.
     */
    @WithDefault(ConfigConstants.DUMMY_VALUE)
    Optional<String> resourceName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.domain-name}
     * specifically for chat models if it is set.
     */
    @WithDefault(ConfigConstants.DUMMY_VALUE)
    Optional<String> domainName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.deployment-name}
     * specifically for chat models if it is set.
     */
    @WithDefault(ConfigConstants.DUMMY_VALUE)
    Optional<String> deploymentName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.endpoint}
     * specifically for chat models if it is set.
     */
    @WithDefault(ConfigConstants.DUMMY_VALUE)
    Optional<String> endpoint();

    /**
     * The Azure AD token to use for this operation.
     * If present, then the requests towards OpenAI will include this in the Authorization header.
     * Note that this property overrides the functionality of {@code quarkus.langchain4j.azure-openai.embedding-model.api-key}.
     */
    Optional<String> adToken();

    /**
     * The API version to use for this operation. This follows the YYYY-MM-DD format
     */
    Optional<String> apiVersion();

    /**
     * Azure OpenAI API key
     */
    Optional<String> apiKey();

    /**
     * What sampling temperature to use, with values between 0 and 2.
     * Higher values means the model will take more risks.
     * A value of 0.9 is good for more creative applications, while 0 (argmax sampling) is good for ones with a well-defined
     * answer.
     * It is recommended to alter this or topP, but not both.
     */
    @WithDefault("${quarkus.langchain4j.temperature:1.0}")
    Double temperature();

    /**
     * An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens
     * with topP probability mass.
     * 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * It is recommended to alter this or temperature, but not both.
     */
    @WithDefault("1.0")
    Double topP();

    /**
     * If specified, our system will make the best effort to sample deterministically, such that repeated requests with the same
     * seed and parameters should return the same result. Determinism isn't guaranteed.
     * Support for reproducible output was first added in API version 2023-12-01-preview
     */
    Optional<Integer> seed();

    /**
     * The maximum number of tokens to generate in the completion. The token count of your prompt plus max_tokens can't exceed
     * the model's context length.
     * Most models have a context length of 2048 tokens (except for the newest models, which support 4096).
     */
    Optional<Integer> maxTokens();

    /**
     * Number between -2.0 and 2.0.
     * Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to
     * talk about new topics.
     */
    @WithDefault("0")
    Double presencePenalty();

    /**
     * Number between -2.0 and 2.0.
     * Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's
     * likelihood to repeat the same line verbatim.
     */
    @WithDefault("0")
    Double frequencyPenalty();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * The response format the model should use.
     * Some models are not compatible with some response formats, make sure to review OpenAI documentation.
     */
    Optional<String> responseFormat();
}
