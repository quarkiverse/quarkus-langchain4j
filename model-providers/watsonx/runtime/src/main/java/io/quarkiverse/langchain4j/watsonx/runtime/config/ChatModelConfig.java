package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.model.chat.request.ToolChoice;
import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Specifies the model to use for the chat completion.
     * <p>
     * A list of all available models is provided in the IBM watsonx.ai documentation at the
     * <a href="https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided">this
     * link</a>.
     * <p>
     * To use a model, locate the <code>API model ID</code> column in the table and copy the corresponding model ID.
     */
    @WithDefault("meta-llama/llama-4-maverick-17b-128e-instruct-fp8")
    String modelId();

    /**
     * Specifies how the model should choose which tool to call during a request.
     * <p>
     * This value can be:
     * <ul>
     * <li><b>auto</b>: The model decides whether and which tool to call automatically.</li>
     * <li><b>required</b>: The model must call one of the available tools.</li>
     * </ul>
     * <p>
     * If {@code toolChoiceName} is set, this value is ignored.
     * <p>
     * Setting this value influences the tool-calling behavior of the model when no specific tool is required.
     */
    Optional<ToolChoice> toolChoice();

    /**
     * Specifies the name of a specific tool that the model must call.
     * <p>
     * When set, the model will be forced to call the specified tool. The
     * name must exactly match one of the available tools defined for the service.
     */
    Optional<String> toolChoiceName();

    /**
     * Positive values penalize new tokens based on their existing frequency in the generated text, reducing the likelihood of
     * the model repeating the
     * same lines verbatim.
     * <p>
     * <strong>Possible values:</strong> <code>-2 &lt; value &lt; 2</code>
     */
    @WithDefault("0")
    Double frequencyPenalty();

    /**
     * Specifies whether to return the log probabilities of the output tokens.
     * <p>
     * If set to {@code true}, the response will include the log probability of each output token in the content of the message.
     */
    @WithDefault("false")
    Boolean logprobs();

    /**
     * An integer specifying the number of most likely tokens to return at each token position, each with an associated log
     * probability. The option
     * <code>logprobs</code> must be set to <code>true</code> if this parameter is used.
     * <p>
     * <strong>Possible values:</strong> <code>0 ≤ value ≤ 20</code>
     */
    Optional<Integer> topLogprobs();

    /**
     * The maximum number of tokens that can be generated in the chat completion. The total length of input tokens and generated
     * tokens is limited by the
     * model's context length. Set to 0 for the model's configured max generated tokens.
     */
    @WithDefault("1024")
    Integer maxTokens();

    /**
     * Specifies how many chat completion choices to generate for each input message.
     */
    @WithDefault("1")
    Integer n();

    /**
     * Applies a penalty to new tokens based on whether they already appear in the generated text so far, encouraging the model
     * to introduce new topics
     * rather than repeat itself.
     * <p>
     * <strong>Possible values:</strong> <code>-2 < value < 2</code>
     */
    @WithDefault("0")
    Double presencePenalty();

    /**
     * Random number generator seed to use in sampling mode for experimental repeatability.
     */
    Optional<Integer> seed();

    /**
     * Defines one or more stop sequences that will cause the model to stop generating further tokens if any of them are
     * encountered in the output.
     * <p>
     * This allows control over where the model should end its response. If a stop sequence is encountered before the minimum
     * number of tokens has been
     * generated, it will be ignored.
     * <p>
     * <strong>Possible values:</strong> <code>0 ≤ number of items ≤ 4</code>
     */
    Optional<List<String>> stop();

    /**
     * Specifies the sampling temperature to use in the generation process.
     * <p>
     * Higher values (e.g. <code>0.8</code>) make the output more random and diverse, while lower values (e.g. <code>0.2</code>)
     * make the output more
     * focused and deterministic.
     * <p>
     *
     * <strong>Possible values:</strong> <code>0 < value < 2</code>
     */
    @WithDefault("${quarkus.langchain4j.temperature:1.0}")
    Double temperature();

    /**
     * An alternative to sampling with <code>temperature</code>, called nucleus sampling, where the model considers the results
     * of the tokens with
     * <code>top_p</code> probability mass. So <code>0.1</code> means only the tokens comprising the top 10% probability mass
     * are considered.
     * <p>
     * <strong>Possible values:</strong> <code>0 < value < 1</code>
     */
    @WithDefault("1")
    Double topP();

    /**
     * Specifies the desired format for the model's output.
     * <p>
     * <strong>Allowable values:</strong> <code>[json_object]</code>
     */
    Optional<String> responseFormat();

    /**
     * Whether chat model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
