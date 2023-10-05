package io.quarkiverse.langchain4j.runtime;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface LocalAi {

    /**
     * Base URL of OpenAI API
     */
    Optional<String> baseUrl();

    /**
     * Model name to use
     */
    @WithDefault("gpt-3.5-turbo")
    String modelName();

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
     * What sampling temperature to use, with values between 0 and 2.
     * Higher values means the model will take more risks.
     * A value of 0.9 is good for more creative applications, while 0 (argmax sampling) is good for ones with a well-defined
     * answer.
     * It is recommended to alter this or topP, but not both.
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens
     * with topP probability mass.
     * 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * It is recommended to alter this or topP, but not both.
     */
    @WithDefault("1.0")
    Double topP();

    /**
     * The maximum number of tokens to generate in the completion. The token count of your prompt plus max_tokens can't exceed
     * the model's context length.
     * Most models have a context length of 2048 tokens (except for the newest models, which support 4096).
     */
    @WithDefault("16")
    Integer maxTokens();

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

}
