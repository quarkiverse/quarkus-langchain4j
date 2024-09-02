package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Represents the strategy used for picking the tokens during generation of the output text. During text generation when
     * parameter
     * value is set to <code>greedy</code>, each successive token corresponds to the highest probability token given the text
     * that has
     * already been generated. This strategy can lead to repetitive results especially for longer output sequences. The
     * alternative
     * <code>sample</code> strategy generates text by picking subsequent tokens based on the probability distribution of
     * possible next
     * tokens defined by (i.e., conditioned on) the already-generated text and the <code>top_k</code> and <code>top_p</code>
     * parameters.
     * <p>
     * <strong>Allowable values:</strong> <code>[sample,greedy]</code>
     */
    @WithDefault("greedy")
    String decodingMethod();

    /**
     * It can be used to exponentially increase the likelihood of the text generation terminating once a specified number of
     * tokens
     * have been generated.
     */
    LengthPenaltyConfig lengthPenalty();

    /**
     * The maximum number of new tokens to be generated. The maximum supported value for this field depends on the model being
     * used.
     * How the "token" is defined depends on the tokenizer and vocabulary size, which in turn depends on the model. Often the
     * tokens
     * are a mix of full words and sub-words. Depending on the users plan, and on the model being used, there may be an enforced
     * maximum number of new tokens.
     * <p>
     * <strong>Possible values:</strong> <code>≥ 0</code>
     */
    @WithDefault("200")
    Integer maxNewTokens();

    /**
     * If stop sequences are given, they are ignored until minimum tokens are generated.
     * <p>
     * <strong>Possible values:</strong> <code>≥ 0</code>
     */
    @WithDefault("0")
    Integer minNewTokens();

    /**
     * Random number generator seed to use in sampling mode for experimental repeatability.
     * <p>
     * <strong>Possible values:</strong> <code>≥ 1</code>
     */
    Optional<Integer> randomSeed();

    /**
     * Stop sequences are one or more strings which will cause the text generation to stop if/when they are produced as part of
     * the
     * output. Stop sequences encountered prior to the minimum number of tokens being generated will be ignored.
     * <p>
     * <strong>Possible values:</strong> <code>0 ≤ number of items ≤ 6, contains only unique items</code>
     */
    Optional<List<String>> stopSequences();

    /**
     * A value used to modify the next-token probabilities in <code>sampling</code> mode. Values less than <code>1.0</code>
     * sharpen
     * the probability distribution, resulting in "less random" output. Values greater than <code>1.0</code> flatten the
     * probability
     * distribution, resulting in "more random" output. A value of <code>1.0</code> has no effect.
     * <p>
     * <strong>Possible values:</strong> <code>0 ≤ value ≤ 2</code>
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * The number of highest probability vocabulary tokens to keep for top-k-filtering. Only applies for <code>sampling</code>
     * mode.
     * When decoding_strategy is set to <code>sample</code>, only the <code>top_k</code> most likely tokens are considered as
     * candidates for the next generated token.
     * <p>
     * <strong>Possible values:</strong> <code>1 ≤ value ≤ 100</code>
     */
    Optional<Integer> topK();

    /**
     * Similar to <code>top_k</code> except the candidates to generate the next token are the most likely tokens with
     * probabilities
     * that add up to at least <code>top_p</code>. Also known as nucleus sampling. A value of <code>1.0</code> is equivalent to
     * disabled.
     * <p>
     * <strong>Possible values:</strong> <code>0 < value ≤ 1</code>
     */
    Optional<Double> topP();

    /**
     * Represents the penalty for penalizing tokens that have already been generated or belong to the context. The value
     * <code>1.0</code> means that there is no penalty.
     * <p>
     * <strong>Possible values:</strong> <code>1 ≤ value ≤ 2</code>
     */
    Optional<Double> repetitionPenalty();

    /**
     * Represents the maximum number of input tokens accepted. This can be used to avoid requests failing due to input being
     * longer
     * than configured limits. If the text is truncated, then it truncates the start of the input (on the left), so the end of
     * the
     * input will remain the same. If this value exceeds the maximum sequence length (refer to the documentation to find this
     * value
     * for the model) then the call will fail if the total number of tokens exceeds the maximum sequence length. Zero means
     * don't
     * truncate.
     * <p>
     * <strong>Possible values:</strong> <code>≥ 0</code>
     */
    Optional<Integer> truncateInputTokens();

    /**
     * Pass <code>false</code> to omit matched stop sequences from the end of the output text. The default is <code>true</code>,
     * meaning that the output will end with the stop sequence text when matched.
     */
    Optional<Boolean> includeStopSequence();

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

    /**
     * Delimiter used to concatenate the ChatMessage elements into a single string. By setting this property, you can define
     * your
     * preferred way of concatenating messages to ensure that the prompt is structured in the correct way.
     */
    @WithDefault("\n")
    String promptJoiner();

    @ConfigGroup
    public interface LengthPenaltyConfig {

        /**
         * Represents the factor of exponential decay. Larger values correspond to more aggressive decay.
         * <p>
         * <strong>Possible values:</strong> <code>> 1</code>
         */
        Optional<Double> decayFactor();

        /**
         * A number of generated tokens after which this should take effect.
         * <p>
         * <strong>Possible values:</strong> <code>≥ 0</code>
         */
        Optional<Integer> startIndex();
    }
}
