package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model id to use.
     * <p>
     * To view the complete model list, <a href=
     * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-api-model-ids.html?context=wx&audience=wdp#model-ids">click
     * here</a>.
     */
    @WithDefault("mistralai/mistral-large")
    String modelId();

    /**
     * Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's
     * likelihood to
     * repeat the same line verbatim.
     * <p>
     * <strong>Possible values:</strong> <code>-2 < value < 2</code>
     */
    @WithDefault("0")
    Double frequencyPenalty();

    /**
     * Whether to return log probabilities of the output tokens or not. If true, returns the log probabilities of each output
     * token
     * returned in the content of message.
     */
    @WithDefault("false")
    Boolean logprobs();

    /**
     * An integer specifying the number of most likely tokens to return at each token position, each with an associated log
     * probability. The option <code>logprobs</code> must be set to <code>true</code> if this parameter is used.
     * <p>
     * <strong>Possible values:</strong> <code>0 ≤ value ≤ 20</code> *
     */
    Optional<Integer> topLogprobs();

    /**
     * The maximum number of tokens that can be generated in the chat completion. The total length of input tokens and generated
     * tokens is limited by the model's context length.
     */
    @WithDefault("1024")
    Integer maxTokens();

    /**
     * How many chat completion choices to generate for each input message. Note that you will be charged based on the number of
     * generated tokens across all of the choices. Keep n as 1 to minimize costs
     */
    @WithDefault("1")
    Integer n();

    /**
     * Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to
     * talk
     * about new topics.
     * <p>
     * <strong>Possible values:</strong> <code>-2 < value < 2</code>
     */
    @WithDefault("0")
    Double presencePenalty();

    /**
     * What sampling temperature to use,. Higher values like <code>0.8</code> will make the output more random, while lower
     * values
     * like <code>0.2</code> will make it more focused and deterministic.
     * <p>
     * We generally recommend altering this or <code>top_p</code> but not both.
     * <p>
     * <strong>Possible values:</strong> <code>0 < value < 2</code>
     */
    @WithDefault("${quarkus.langchain4j.temperature:1.0}")
    Double temperature();

    /**
     *
     * An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens
     * with
     * top_p probability mass. So <code>0.1</code> means only the tokens comprising the top 10% probability mass are considered.
     * <p>
     * We generally recommend altering this or <code>temperature</code> but not both.
     * <p>
     * <strong>Possible values:</strong> <code>0 < value < 1</code>
     */
    @WithDefault("1")
    Double topP();

    /**
     * Specifies the desired format for the model's output.
     * <p>
     * <strong>Allowable values:</strong> <code>[json_object]</code> *
     * <p>
     * <strong>Applicable in modes:</strong> <code>[chat]</code>
     */
    Optional<String> responseFormat();

    /**
     * Whether chat model requests should be logged.
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.watsonx.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged.
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.watsonx.log-responses}")
    Optional<Boolean> logResponses();
}
