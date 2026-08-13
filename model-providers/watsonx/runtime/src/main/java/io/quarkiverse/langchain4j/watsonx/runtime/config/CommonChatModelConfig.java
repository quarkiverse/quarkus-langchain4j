package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.List;
import java.util.Optional;

import com.ibm.watsonx.ai.chat.model.BaseChatParameters.ResponseFormat;

import dev.langchain4j.model.chat.request.ToolChoice;
import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.smallrye.config.WithDefault;

public interface CommonChatModelConfig {

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
     * When set, the model will be forced to call the specified tool. The name must exactly match one of the available tools
     * defined for the service.
     */
    Optional<String> toolChoiceName();

    /**
     * Positive values penalize new tokens based on their existing frequency in the generated text, reducing the likelihood of
     * the model repeating the
     * same lines verbatim.
     * <p>
     * The parameter is sent to the model only when it is set.
     * <p>
     * <strong>Possible values:</strong> <code>-2 &lt; value &lt; 2</code>
     */
    Optional<Double> frequencyPenalty();

    /**
     * Specifies whether to return the log probabilities of the output tokens.
     * <p>
     * If set to {@code true}, the response will include the log probability of each output token in the content of the message.
     * <p>
     * The parameter is sent to the model only when it is set.
     */
    Optional<Boolean> logprobs();

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
     * tokens is limited by
     * the model's context length. Set to 0 for the model's configured max generated tokens.
     */
    @WithDefault("1024")
    Integer maxOutputTokens();

    /**
     * Applies a penalty to new tokens based on whether they already appear in the generated text so far, encouraging the model
     * to introduce new
     * topics rather than repeat itself.
     * <p>
     * The parameter is sent to the model only when it is set.
     * <p>
     * <strong>Possible values:</strong> <code>-2 &lt; value &lt; 2</code>
     */
    Optional<Double> presencePenalty();

    /**
     * Random number generator seed to use in sampling mode for experimental repeatability.
     */
    Optional<Integer> seed();

    /**
     * Defines one or more stop sequences that will cause the model to stop generating further tokens if any of them are
     * encountered in the output.
     * <p>
     * This allows control over where the model should end its response. If a stop sequence is encountered before the minimum
     * number of tokens has
     * been generated, it will be ignored.
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
     * The parameter is sent to the model only when it is set.
     * <p>
     * <strong>Possible values:</strong> <code>0 &lt; value &lt; 1</code>
     */
    Optional<Double> topP();

    /**
     * Specifies the desired format for the model's output.
     * <p>
     * <strong>Allowable values:</strong> <code>[text, json, json_schema]</code>
     */
    Optional<ResponseFormat> responseFormat();

    /**
     * Whether the JSON Schema sent to the model should use the strict mode.
     * <p>
     * When enabled, the model is constrained to return a response that exactly matches the given JSON Schema. To satisfy the
     * restrictions of the strict mode, all the properties of the schema are marked as required, the optional ones are made
     * nullable and <code>additionalProperties</code> is set to <code>false</code>.
     * <p>
     * Set this property to <code>false</code> to let the model treat the schema as a hint instead of a constraint.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> strictJsonSchema();

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

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();
}
