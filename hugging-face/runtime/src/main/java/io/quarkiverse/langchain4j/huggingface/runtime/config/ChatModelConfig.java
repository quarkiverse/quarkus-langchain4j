package io.quarkiverse.langchain4j.huggingface.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    /**
     * Model to use
     */
    @WithDefault("tiiuae/falcon-7b-instruct")
    String modelId();

    /**
     * Float (0.0-100.0). The temperature of the sampling operation. 1 means regular sampling, 0 means always take the highest
     * score, 100.0 is getting closer to uniform probability
     */
    @WithDefault("1.0")
    Double temperature();

    /**
     * Int (0-250). The amount of new tokens to be generated, this does not include the input length it is a estimate of the
     * size of generated text you want. Each new tokens slows down the request, so look for balance between response times and
     * length of text generated
     */
    Optional<Integer> maxNewTokens();

    /**
     * If set to {@code false}, the return results will not contain the original query making it easier for prompting
     */
    @WithDefault("false")
    Boolean returnFullText();

    /**
     * If the model is not ready, wait for it instead of receiving 503. It limits the number of requests required to get your
     * inference done. It is advised to only set this flag to true after receiving a 503 error as it will limit hanging in your
     * application to known places
     */
    @WithDefault("true")
    Boolean waitForModel();
}
