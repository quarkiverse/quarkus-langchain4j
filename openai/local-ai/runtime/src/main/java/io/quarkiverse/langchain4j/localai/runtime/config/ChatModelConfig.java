package io.quarkiverse.langchain4j.localai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model name to use
     */
    String modelName();

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
    Optional<Integer> maxTokens();
}
