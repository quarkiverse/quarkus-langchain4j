package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use.
     * <p>
     * For a complete list of models, visit:
     * {@link https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx}
     */
    @WithDefault("ibm/granite-20b-multilingual")
    String modelId();

    /**
     * Represents the strategy used for picking the tokens during generation of the output text. Options are greedy and sample.
     * Value defaults to sample if not specified.
     * <p>
     * During text generation when parameter value is set to greedy, each successive token corresponds to the highest
     * probability token given the text that has already been generated.
     * This strategy can lead to repetitive results especially for longer output sequences.
     * The alternative sample strategy generates text by picking subsequent tokens based on the probability distribution of
     * possible next tokens defined by (i.e., conditioned on)
     * the already-generated text and the top_k and top_p parameters described below. See this url for an informative article
     * about text generation.
     */
    @WithDefault("greedy")
    String decodingMethod();

    /**
     * A value used to modify the next-token probabilities in sampling mode.
     * Values less than 1.0 sharpen the probability distribution, resulting in "less random" output.
     * Values greater than 1.0 flatten the probability distribution, resulting in "more random" output. A value of 1.0 has no
     * effect and is the default.
     * The allowed range is 0.0 to 2.0.
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * If stop sequences are given, they are ignored until minimum tokens are generated. Defaults to 0.
     */
    @WithDefault("0")
    Integer minNewTokens();

    /**
     * The maximum number of new tokens to be generated. The range is 0 to 1024.
     */
    @WithDefault("200")
    Integer maxNewTokens();

    /**
     * Random number generator seed to use in sampling mode for experimental
     * repeatability. Must be >= 1.
     */
    Optional<Integer> randomSeed();

    /**
     * Stop sequences are one or more strings which will cause the text generation
     * to stop if/when they are produced as part of the output. Stop sequences
     * encountered prior to the minimum number of tokens being generated will be
     * ignored. The list may contain up to 6 strings.
     */
    Optional<List<String>> stopSequences();

    /**
     * The number of highest probability vocabulary tokens to keep for
     * top-k-filtering. Only applies for sampling mode, with range from 1 to 100.
     * When decoding_strategy is set to sample, only the top_k most likely tokens
     * are considered as candidates for the next generated token.
     */
    Optional<Integer> topK();

    /**
     * Similar to top_k except the candidates to generate the next token are the
     * most likely tokens with probabilities that add up to at least top_p. The
     * valid range is 0.0 to 1.0 where 1.0 is equivalent to disabled and is the
     * default. Also known as nucleus sampling.
     */
    Optional<Double> topP();

    /**
     * Represents the penalty for penalizing tokens that have already been generated
     * or belong to the context. The range is 1.0 to 2.0 and defaults to 1.0 (no
     * penalty).
     */
    Optional<Double> repetitionPenalty();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.watsonx.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.watsonx.log-responses}")
    Optional<Boolean> logResponses();
}
