package io.quarkiverse.langchain4j.openai.runtime.config;

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
    @WithDefault("gpt-image-1")
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
    @ConfigDocDefault("${java.io.tmpdir}/openai-images")
    Optional<Path> persistDirectory();

    /**
     * The size of the generated images.
     * <p>
     * Supported values: {@code 1024x1024}, {@code 1536x1024}, {@code 1024x1536}, or {@code auto}.
     */
    @WithDefault("1024x1024")
    String size();

    /**
     * The quality of the image that will be generated.
     * <p>
     * Supported values: {@code auto}, {@code low}, {@code medium}, or {@code high}.
     */
    @WithDefault("auto")
    String quality();

    /**
     * The number of images to generate.
     * <p>
     * Must be between 1 and 10.
     */
    @WithDefault("1")
    int number();

    /**
     * A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
     */
    Optional<String> user();

    /**
     * The output format of the generated image.
     * <p>
     * Supported values: {@code png}, {@code jpeg}, {@code webp}.
     * <p>
     * Only supported for {@code gpt-image-1} and newer models.
     */
    Optional<String> outputFormat();

    /**
     * The background type for the generated image.
     * <p>
     * Supported values: {@code transparent}, {@code opaque}, {@code auto}.
     * <p>
     * Only supported for {@code gpt-image-1} and newer models.
     * Transparent backgrounds require {@code output-format} set to {@code png} or {@code webp}.
     */
    Optional<String> background();

    /**
     * The compression level for the generated image (0-100).
     * <p>
     * Only supported for {@code gpt-image-1} and newer models with {@code jpeg} or {@code webp} output formats.
     */
    Optional<Integer> outputCompression();

    /**
     * The moderation level for the image generation request.
     * <p>
     * Supported values: {@code low}, {@code auto}.
     * <p>
     * Only supported for {@code gpt-image-1} and newer models.
     */
    Optional<String> moderation();

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
