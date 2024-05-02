package io.quarkiverse.langchain4j.azure.openai.runtime.config;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ImageModelConfig {

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
    @WithDefault("${quarkus.langchain4j.azure-openai.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether image model responses should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.azure-openai.log-responses}")
    Optional<Boolean> logResponses();
}
