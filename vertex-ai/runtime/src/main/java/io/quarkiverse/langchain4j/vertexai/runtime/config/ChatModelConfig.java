package io.quarkiverse.langchain4j.vertexai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * The id of the model to use
     */
    @WithDefault("chat-bison")
    String modelId();

    /**
     * The temperature is used for sampling during response generation, which occurs when topP and topK are applied.
     * Temperature controls the degree of randomness in token selection.
     * Lower temperatures are good for prompts that require a less open-ended or creative response, while higher temperatures
     * can lead to more diverse or creative results.
     * A temperature of 0 means that the highest probability tokens are always selected. In this case, responses for a given
     * prompt are mostly deterministic, but a small amount of variation is still possible.
     * <p>
     * If the model returns a response that's too generic, too short, or the model gives a fallback response, try increasing the
     * temperature.
     */
    @WithDefault("0.0")
    Double temperature();

    /**
     * Maximum number of tokens that can be generated in the response. A token is approximately four characters. 100 tokens
     * correspond to roughly 60-80 words.
     * Specify a lower value for shorter responses and a higher value for potentially longer responses.
     */
    @WithDefault("1024")
    Integer maxOutputTokens();

    /**
     * Top-P changes how the model selects tokens for output. Tokens are selected from the most (see top-K) to least probable
     * until the sum of their probabilities equals the top-P value.
     * For example, if tokens A, B, and C have a probability of 0.3, 0.2, and 0.1 and the top-P value is 0.5, then the model
     * will select either A or B as the next token by using temperature and excludes C as a candidate.
     * <p>
     * Specify a lower value for less random responses and a higher value for more random responses.
     */
    @WithDefault("0.95")
    Double topP();

    /**
     * Top-K changes how the model selects tokens for output. A top-K of 1 means the next selected token is the most probable
     * among all tokens in the model's vocabulary (also called greedy decoding),
     * while a top-K of 3 means that the next token is selected from among the three most probable tokens by using temperature.
     * <p>
     * For each token selection step, the top-K tokens with the highest probabilities are sampled. Then tokens are further
     * filtered based on top-P with the final token selected using temperature sampling.
     * <p>
     * Specify a lower value for less random responses and a higher value for more random responses.
     */
    @WithDefault("40")
    Integer topK();

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
}
