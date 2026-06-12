package io.quarkiverse.langchain4j.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;
import org.jboss.logging.Logger;

/**
 * Holds a single loaded GPULlama3 model instance, shared between
 * {@link GPULlama3ChatModel} and {@link GPULlama3StreamingChatModel}
 * for the same configuration, so the model weights are only loaded
 * into GPU memory once.
 */
public class GPULlama3ModelHolder {

    private static final Logger LOG = Logger.getLogger(GPULlama3ModelHolder.class);

    private final Optional<Path> modelCachePath;
    private final String modelName;
    private final String quantization;
    private final double temperature;
    private final double topP;
    private final int seed;
    final int maxTokens;
    final boolean onGPU;
    private final boolean withPrefillDecode;
    private final int prefillBatchSize;

    // force happens-before relationship between initialization and usage
    private volatile boolean initialized = false;

    Model model;
    State state;
    Sampler sampler;
    ChatFormat chatFormat;
    TornadoVMMasterPlan tornadoVMPlan;

    public GPULlama3ModelHolder(
            Optional<Path> modelCachePath,
            String modelName,
            String quantization,
            Double temperature,
            Double topP,
            Integer seed,
            Integer maxTokens,
            Boolean onGPU,
            Boolean withPrefillDecode,
            Integer prefillBatchSize) {
        DefaultConfig defaultConfig = defaultConfigForModel(modelName);
        this.modelCachePath = modelCachePath;
        this.modelName = modelName;
        this.quantization = quantization;
        this.temperature = getOrDefault(temperature, defaultConfig.temperature());
        this.topP = getOrDefault(topP, defaultConfig.topP());
        this.seed = getOrDefault(seed, ThreadLocalRandom.current().nextInt());
        this.maxTokens = getOrDefault(maxTokens, defaultConfig.maxTokens());
        this.onGPU = getOrDefault(onGPU, Boolean.TRUE);
        this.withPrefillDecode = getOrDefault(withPrefillDecode, Boolean.TRUE);
        this.prefillBatchSize = getOrDefault(prefillBatchSize, 32);
    }

    private static DefaultConfig defaultConfigForModel(String modelName) {
        String normalizedName = modelName != null ? modelName.toLowerCase() : "";
        if (normalizedName.contains("qwen")) {
            return new DefaultConfig(0.8, 0.9, 2048);
        }
        if (normalizedName.contains("llama")) {
            return new DefaultConfig(0.3, 0.95, 2048);
        }
        return new DefaultConfig(0.7, 0.9, 2048);
    }

    private record DefaultConfig(double temperature, double topP, int maxTokens) {
    }

    public synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        GPULlama3ModelRegistry registry = GPULlama3ModelRegistry.getOrCreate(modelCachePath);
        try {
            Path modelPath = registry.downloadModel(modelName, quantization, Optional.empty(), Optional.empty());

            // The engine reads these JVM-global flags during class loading, so set them
            // before ModelLoader initializes the model and TornadoVM plan.
            System.setProperty("llama.withPrefillDecode", Boolean.toString(withPrefillDecode));
            System.setProperty("llama.prefillBatchSize", Integer.toString(prefillBatchSize));

            LOG.info("GPULlama3 model initialization {modelPath=" + modelPath
                    + ", temperature=" + temperature
                    + ", topP=" + topP
                    + ", seed=" + seed
                    + ", maxTokens=" + maxTokens
                    + ", onGPU=" + onGPU
                    + ", withPrefillDecode=" + withPrefillDecode
                    + ", prefillBatchSize=" + prefillBatchSize + "}...");

            this.model = ModelLoader.loadModel(modelPath, maxTokens, true, onGPU);
            this.state = model.createNewState();
            this.sampler = Sampler.selectSampler(
                    model.configuration().vocabularySize(),
                    (float) temperature,
                    (float) topP,
                    seed);
            this.chatFormat = model.chatFormat();
            if (onGPU) {
                this.tornadoVMPlan = TornadoVMMasterPlan.initializeTornadoVMPlan(state, model);
            }

            initialized = true;
            LOG.info("GPULlama3 model initialization complete!");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
