package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Background;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Moderation;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.ResponseFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Style;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface GatewayImageModelConfig {

    /**
     * The identifier of the model to use, as configured in the Model Gateway.
     */
    Optional<String> modelName();

    /**
     * The background of the generated images.
     * <p>
     * Transparency requires an output format that supports it, so <code>png</code> or <code>webp</code>.
     * <p>
     * <strong>Allowable values:</strong> <code>[transparent, opaque, auto]</code>
     */
    Optional<Background> background();

    /**
     * How strictly the generated images are filtered, <code>low</code> being the least restrictive.
     * <p>
     * <strong>Allowable values:</strong> <code>[low, auto]</code>
     */
    Optional<Moderation> moderation();

    /**
     * The compression level of the generated images, from <code>0</code> to <code>100</code>.
     * <p>
     * Only the <code>jpeg</code> and <code>webp</code> output formats accept it.
     */
    Optional<Integer> outputCompression();

    /**
     * The file format of the generated images.
     * <p>
     * <strong>Allowable values:</strong> <code>[png, jpeg, webp, auto]</code>
     */
    Optional<OutputFormat> outputFormat();

    /**
     * The quality of the generated images.
     * <p>
     * <strong>Allowable values:</strong> <code>[auto, high, medium, low, hd, standard]</code>
     */
    Optional<Quality> quality();

    /**
     * How the generated images are returned, as a link with <code>url</code> or as Base64 data with <code>b64_json</code>.
     * <p>
     * Only the models that support it accept this value.
     * <p>
     * <strong>Allowable values:</strong> <code>[url, b64_json]</code>
     */
    Optional<ResponseFormat> responseFormat();

    /**
     * The dimensions of the generated images, for example <code>1024x1024</code>.
     */
    Optional<String> size();

    /**
     * The visual style of the generated images.
     * <p>
     * <strong>Allowable values:</strong> <code>[vivid, natural]</code>
     */
    Optional<Style> style();

    /**
     * A stable identifier of the end user issuing the request, used by the backing provider to detect and prevent abuse.
     */
    Optional<String> user();

    /**
     * Whether image model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether image model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();
}
