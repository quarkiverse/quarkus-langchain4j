package io.quarkiverse.langchain4j.huggingface.runtime.config;

import java.net.URL;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig {

    String DEFAULT_INFERENCE_ENDPOINT = "https://api-inference.huggingface.co/models/tiiuae/falcon-7b-instruct";

    /**
     * The URL of the inference endpoint for the chat model.
     * <p>
     * When using Hugging Face with the inference API, the URL is
     * {@code https://api-inference.huggingface.co/models/<model-id>},
     * for example {@code https://api-inference.huggingface.co/models/google/flan-t5-small}.
     * <p>
     * When using a deployed inference endpoint, the URL is the URL of the endpoint.
     * When using a local hugging face model, the URL is the URL of the local model.
     */
    @WithDefault(DEFAULT_INFERENCE_ENDPOINT)
    URL inferenceEndpointUrl();

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
    Optional<Boolean> returnFullText();

    /**
     * If the model is not ready, wait for it instead of receiving 503. It limits the number of requests required to get your
     * inference done. It is advised to only set this flag to true after receiving a 503 error as it will limit hanging in your
     * application to known places
     */
    @WithDefault("true")
    Boolean waitForModel();
}
