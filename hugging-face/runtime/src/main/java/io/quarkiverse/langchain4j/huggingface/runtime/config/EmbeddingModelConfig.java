package io.quarkiverse.langchain4j.huggingface.runtime.config;

import java.net.URL;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    String DEFAULT_INFERENCE_ENDPOINT_EMBEDDING = "https://api-inference.huggingface.co/pipeline/feature-extraction/sentence-transformers/all-MiniLM-L6-v2";

    /**
     * The URL of the inference endpoint for the embedding.
     * <p>
     * When using Hugging Face with the inference API, the URL is
     * {@code https://api-inference.huggingface.co/pipeline/feature-extraction/<model-id>},
     * for example
     * {@code https://api-inference.huggingface.co/pipeline/feature-extraction/sentence-transformers/all-mpnet-base-v2}.
     * <p>
     * When using a deployed inference endpoint, the URL is the URL of the endpoint.
     * When using a local hugging face model, the URL is the URL of the local model.
     */
    @WithDefault(DEFAULT_INFERENCE_ENDPOINT_EMBEDDING)
    Optional<URL> inferenceEndpointUrl();

    /**
     * If the model is not ready, wait for it instead of receiving 503. It limits the number of requests required to get your
     * inference done. It is advised to only set this flag to true after receiving a 503 error as it will limit hanging in your
     * application to known places
     */
    @WithDefault("true")
    Boolean waitForModel();
}
