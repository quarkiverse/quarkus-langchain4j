package io.quarkiverse.langchain4j.jlama;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.util.ProgressReporter;

/**
 * A registry for managing Jlama models on local disk.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class JlamaModelRegistry {

    private static final String DEFAULT_MODEL_CACHE_PATH = System.getProperty("user.home", "") + File.separator + ".langchain4j"
            + File.separator + "models";
    private final Path modelCachePath;

    private JlamaModelRegistry(Path modelCachePath) {
        this.modelCachePath = modelCachePath;
        if (!Files.exists(modelCachePath)) {
            try {
                Files.createDirectories(modelCachePath);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
    }

    public static JlamaModelRegistry getOrCreate(Optional<Path> modelCachePath) {
        return new JlamaModelRegistry(modelCachePath.orElse(Path.of(DEFAULT_MODEL_CACHE_PATH)));
    }

    public Path getModelCachePath() {
        return modelCachePath;
    }

    public JlamaModel downloadModel(String modelName, Optional<String> authToken) throws IOException {
        return downloadModel(modelName, authToken, Optional.empty());
    }

    public JlamaModel downloadModel(String modelName, Optional<String> authToken, Optional<ProgressReporter> progressReporter)
            throws IOException {
        ModelInfo modelInfo = ModelInfo.from(modelName);
        File modelDir = SafeTensorSupport.maybeDownloadModel(modelCachePath.toString(), Optional.ofNullable(modelInfo.owner),
                modelInfo.name, true,
                Optional.empty(), authToken, progressReporter);

        File config = new File(modelDir, "config.json");
        ModelSupport.ModelType type = SafeTensorSupport.detectModel(config);
        return new JlamaModel(this, type, modelDir.getName(), Optional.empty(), modelName, true);
    }

    public record ModelInfo(String owner, String name) {

        public static ModelInfo from(String modelName) {
            String[] parts = modelName.split("/");
            if (parts.length == 0 || parts.length > 2) {
                throw new IllegalArgumentException("Model must be in the form owner/name");
            }

            String owner;
            String name;

            if (parts.length == 1) {
                owner = null;
                name = modelName;
            } else {
                owner = parts[0];
                name = parts[1];
            }

            return new ModelInfo(owner, name);
        }

        public String toFileName() {
            return owner + "_" + name;
        }
    }
}
