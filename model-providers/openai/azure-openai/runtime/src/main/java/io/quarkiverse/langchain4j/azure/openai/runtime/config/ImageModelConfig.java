package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ImageModelConfig {
    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.resource-name}
     * specifically for image models if it is set.
     */
    Optional<String> resourceName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.domain-name}
     * specifically for image models if it is set.
     */
    Optional<String> domainName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.deployment-name}
     * specifically for image models if it is set.
     */
    Optional<String> deploymentName();

    /**
     * This property will override the {@code quarkus.langchain4j.azure-openai.endpoint}
     * specifically for image models if it is set.
     */
    Optional<String> endpoint();

    /**
     * The Azure AD token to use for this operation.
     * If present, then the requests towards OpenAI will include this in the Authorization header.
     * Note that this property overrides the functionality of {@code quarkus.langchain4j.azure-openai.embedding-model.api-key}.
     */
    Optional<String> adToken();

    /**
     * The API version to use for this operation. This follows the YYYY-MM-DD format
     */
    Optional<String> apiVersion();

    /**
     * Azure OpenAI API key
     */
    Optional<String> apiKey();

    /**
     * Model name to use
     */
    @WithDefault("dall-e-3")
    String modelName();

    /**
     * Configure whether the generated images will be saved to disk.
     * By default, persisting is disabled, but it is implicitly enabled when
     * {@code quarkus.langchain4j.openai.image-mode.directory} is set and this property is not to {@code false}
     */
    @ConfigDocDefault("false")
    Optional<Boolean> persist();

    /**
     * The path where the generated images will be persisted to disk.
     * This only applies of {@code quarkus.langchain4j.openai.image-mode.persist} is not set to {@code false}.
     */
    @ConfigDocDefault("${java.io.tmpdir}/dall-e-images")
    Optional<Path> persistDirectory();

    /**
     * The format in which the generated images are returned.
     * <p>
     * Must be one of {@code url} or {@code b64_json}
     */
    @WithDefault("url")
    String responseFormat();

    /**
     * The size of the generated images.
     * <p>
     * Must be one of {@code 1024x1024}, {@code 1792x1024}, or {@code 1024x1792} when the model is {@code dall-e-3}.
     * <p>
     * Must be one of {@code 256x256}, {@code 512x512}, or {@code 1024x1024} when the model is {@code dall-e-2}.
     */
    @WithDefault("1024x1024")
    String size();

    /**
     * The quality of the image that will be generated.
     * <p>
     * {@code hd} creates images with finer details and greater consistency across the image.
     * <p>
     * This param is only supported for when the model is {@code dall-e-3}.
     */
    @WithDefault("standard")
    String quality();

    /**
     * The number of images to generate.
     * <p>
     * Must be between 1 and 10.
     * <p>
     * When the model is dall-e-3, only n=1 is supported.
     */
    @WithDefault("1")
    int number();

    /**
     * The style of the generated images.
     * <p>
     * Must be one of {@code vivid} or {@code natural}. Vivid causes the model to lean towards generating hyper-real and
     * dramatic images. Natural causes the model to produce more natural, less hyper-real looking images.
     * <p>
     * This param is only supported for when the model is {@code dall-e-3}.
     */
    @WithDefault("vivid")
    String style();

    /**
     * A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
     */
    Optional<String> user();

    /**
     * Whether image model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether image model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
