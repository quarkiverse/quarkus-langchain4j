package io.quarkiverse.langchain4j.bam.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    @WithDefault("meta-llama/llama-2-70b-chat")
    String modelId();

    /**
     * Version to use
     */
    @WithDefault("2024-01-10")
    String version();

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
}
