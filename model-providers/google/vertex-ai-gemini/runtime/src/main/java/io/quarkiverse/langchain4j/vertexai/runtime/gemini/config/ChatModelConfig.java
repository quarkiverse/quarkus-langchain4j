package io.quarkiverse.langchain4j.vertexai.runtime.gemini.config;

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
    @WithDefault("gemini-3.5-flash")
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
     * Range for gemini-3.5-flash: 0.0 - 2.0
     * <p>
     * Default for gemini-3.5-flash: 1.0
     */
    @WithDefault("${quarkus.langchain4j.temperature}")
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
     * Default for gemini-3.5-flash: 0.95
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
     * Range: 1-40, for models that support setting it
     * <p>
     * gemini-3.5-flash has a fixed topK of 64
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
    @WithDefault("${quarkus.langchain4j.vertexai.gemini.timeout}")
    Optional<Duration> timeout();

    /**
     * Screens prompts and responses with Google Cloud Model Armor (prompt injection, sensitive data, harmful content)
     * before they reach the model or the caller. Requires the Model Armor API and {@code roles/modelarmor.user} for the
     * Vertex AI service agent. A blocked request fails the call; a blocked streamed response finishes with
     * {@code CONTENT_FILTER}.
     *
     * @see <a href=
     *      "https://docs.cloud.google.com/security-command-center/docs/model-armor-vertex-integration">Model Armor
     *      integration with Vertex AI</a>
     */
    ModelArmorConfig modelArmor();

    @ConfigGroup
    interface ModelArmorConfig {

        /**
         * The Model Armor template that screens prompts (prompt injection, sensitive data, harmful content) before they
         * reach the model, in the form {@code projects/PROJECT/locations/LOCATION/templates/TEMPLATE}.
         * The template must be in the same location as the model, and the Vertex AI service agent needs
         * {@code roles/modelarmor.user}. A blocked prompt fails the call.
         */
        Optional<String> promptTemplateName();

        /**
         * The Model Armor template that screens responses before they reach the caller, in the form
         * {@code projects/PROJECT/locations/LOCATION/templates/TEMPLATE}.
         * The template must be in the same location as the model, and the Vertex AI service agent needs
         * {@code roles/modelarmor.user}. A blocked response fails the call; a blocked streamed response finishes with
         * {@code CONTENT_FILTER}.
         */
        Optional<String> responseTemplateName();
    }
}
