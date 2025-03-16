package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model id to use. See <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html">Models
     * Supported</a>
     */
    @ConfigDocDefault("chat: us.amazon.nova-lite-v1:0, stream: anthropic.claude-v2")
    Optional<String> modelId();

    /**
     * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will make the output more random, while
     * lower values like 0.2 will make it more focused and deterministic.
     * <p>
     * It is generally recommended to set this or the {@code top-k} property but not both.
     */
    @ConfigDocDefault("0.7")
    @WithDefault("${quarkus.langchain4j.temperature}")
    OptionalDouble temperature();

    /**
     * The maximum number of tokens to generate in the completion.
     * <p>
     * The token count of your prompt plus {@code max_tokens} cannot exceed the model's context length
     */
    @WithDefault("1024")
    Integer maxTokens();

    /**
     * Double (0.0-1.0). Nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
     * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * <p>
     * It is generally recommended to set this or the {@code temperature} property but not both.
     */
    @ConfigDocDefault("1.0")
    OptionalDouble topP();

    /**
     * Reduces the probability of generating nonsense. A higher value (e.g. 100) will give more diverse answers, while a lower
     * value (e.g. 10) will be more conservative
     */
    @ConfigDocDefault("40")
    OptionalInt topK();

    /**
     * The custom text sequences that will cause the model to stop generating
     */
    Optional<List<String>> stopSequences();

    /**
     * Aws client configuration
     */
    AwsClientConfig client();

}
