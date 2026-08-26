package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters.EncodingFormat;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface GatewayEmbeddingModelConfig extends CommonEmbeddingModelConfig {

    /**
     * The identifier of the model to use, as configured in the Model Gateway (for example
     * <code>openai/text-embedding-3-small</code>).
     * <p>
     * Setting this property routes all embedding requests to the Model Gateway.
     */
    Optional<String> modelName();

    /**
     * The number of dimensions of the returned embeddings.
     * <p>
     * Only the models that support it accept this value.
     */
    Optional<Integer> dimensions();

    /**
     * The wire format used to return the embeddings.
     * <p>
     * Both formats produce the same vectors, <code>base64</code> is decoded for you.
     * <p>
     * <strong>Allowable values:</strong> <code>[float, base64]</code>
     */
    Optional<EncodingFormat> encodingFormat();

    /**
     * A stable identifier of the end user issuing the request, used by the backing provider to detect and prevent abuse.
     */
    Optional<String> user();
}
