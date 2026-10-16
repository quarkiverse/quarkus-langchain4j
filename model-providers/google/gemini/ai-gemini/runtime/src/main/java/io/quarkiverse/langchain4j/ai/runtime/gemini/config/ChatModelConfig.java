package io.quarkiverse.langchain4j.ai.runtime.gemini.config;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * The id of the model to use.
     *
     * @see <a href=
     *      "https://ai.google.dev/gemini-api/docs/models/gemini">https://ai.google.dev/gemini-api/docs/models/gemini</a>
     */
    @WithDefault("gemini-2.5-flash")
    String modelId();

    /**
     * The temperature is used for sampling during response generation, which occurs when topP and topK are applied.
     * Temperature controls the degree of randomness in token selection. Lower temperatures are good for prompts that require a
     * less open-ended or creative response, while higher temperatures can lead to more diverse or creative results.
     * A temperature of 0 means that the highest probability tokens are always selected. In this case, responses for a given
     * prompt are mostly deterministic, but a small amount of variation is still possible.
     * <p>
     * If the model returns a response that's too generic, too short, or the model gives a fallback response, try increasing the
     * temperature.
     * <p>
     * Leave this unset to omit {@code temperature} from the request. Setting it is for older models.
     * Since Gemini 3.6 Flash, temperature, topP, and topK are ignored. Upcoming models reject the request if those
     * fields are present.
     */
    OptionalDouble temperature();

    /**
     * Maximum number of tokens that can be generated in the response. A token is approximately four characters. 100 tokens
     * correspond to roughly 60-80 words.
     * Specify a lower value for shorter responses and a higher value for potentially longer responses.
     */
    @WithDefault("8192")
    Integer maxOutputTokens();

    /**
     * Top-P changes how the model selects tokens for output. Tokens are selected from the most (see top-K) to least probable
     * until the sum of their probabilities equals the top-P value.
     * For example, if tokens A, B, and C have a probability of 0.3, 0.2, and 0.1 and the top-P value is 0.5, then the model
     * will select either A or B as the next token by using temperature and excludes C as a candidate.
     * <p>
     * Specify a lower value for less random responses and a higher value for more random responses.
     * <p>
     * Range: 0.0 - 1.0
     * <p>
     * Default for gemini-2.5-flash: 0.95
     * <p>
     * Leave this unset to omit {@code topP} from the request. Setting it is for older models.
     * Since Gemini 3.6 Flash, temperature, topP, and topK are ignored. Upcoming models reject the request if those
     * fields are present.
     */
    OptionalDouble topP();

    /**
     * Top-K changes how the model selects tokens for output. A top-K of 1 means the next selected token is the most probable
     * among all tokens in the model's vocabulary (also called greedy decoding),
     * while a top-K of 3 means that the next token is selected from among the three most probable tokens by using temperature.
     * <p>
     * For each token selection step, the top-K tokens with the highest probabilities are sampled. Then tokens are further
     * filtered based on top-P with the final token selected using temperature sampling.
     * <p>
     * Specify a lower value for less random responses and a higher value for more random responses.
     * <p>
     * Range: 1-40
     * <p>
     * gemini-2.5-flash doesn't support topK
     * <p>
     * Leave this unset to omit {@code topK} from the request. Setting it is for older models.
     * Since Gemini 3.6 Flash, temperature, topP, and topK are ignored. Upcoming models reject the request if those
     * fields are present.
     */
    OptionalInt topK();

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
     * Global timeout for requests to gemini APIs
     */
    @ConfigDocDefault("10s")
    @WithDefault("${quarkus.langchain4j.ai.gemini.timeout}")
    Optional<Duration> timeout();

    /**
     * Thought related configuration
     */
    ThinkingConfig thinking();

    interface ThinkingConfig {

        /**
         * Controls whether thought summaries are enabled.
         * Thought summaries are synthesized versions of the model's raw thoughts and offer insights into the model's internal
         * reasoning process.
         */
        @WithDefault("false")
        boolean includeThoughts();

        /**
         * The thinkingBudget parameter guides the model on the number of thinking tokens to use when generating a response.
         * A higher token count generally allows for more detailed reasoning, which can be beneficial for tackling more complex
         * tasks.
         * If latency is more important, use a lower budget or disable thinking by setting thinkingBudget to 0.
         * Setting the thinkingBudget to -1 turns on dynamic thinking, meaning the model will adjust the budget based on the
         * complexity of the request.
         * <p>
         * The thinkingBudget is only supported in Gemini 2.5 Flash, 2.5 Pro, and 2.5 Flash-Lite. Depending on the prompt, the
         * model might overflow or underflow the token budget.
         * See <a href="https://ai.google.dev/gemini-api/docs/thinking#set-budget">Gemini API docs</a> for more details.
         * <p>
         * This remains for Gemini 2.5. Upcoming models return HTTP 400 if {@code thinking_budget} is sent.
         * Do not set this together with {@link #thinkingLevel()}.
         */
        OptionalInt thinkingBudget();

        /**
         * How much thinking the model does before answering. Replaces {@code thinkingBudget} for Gemini 3 and later.
         * Allowed values are {@code minimal}, {@code low}, {@code medium}, and {@code high}.
         * Supported levels vary by model. Omit this property to use the model default.
         * Gemini 3 Pro uses {@code low} and {@code high}, while other Gemini 3 models may also accept
         * {@code minimal} and {@code medium}.
         * <p>
         * Do not set this together with {@code thinkingBudget}. Upcoming models reject {@code thinking_budget}.
         *
         * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">https://ai.google.dev/gemini-api/docs/thinking</a>
         */
        Optional<ThinkingLevel> thinkingLevel();
    }
}
