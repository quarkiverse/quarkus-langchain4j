package io.quarkiverse.langchain4j.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

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
            Boolean onGPU) {
        this.modelCachePath = modelCachePath;
        this.modelName = modelName;
        this.quantization = quantization;
        this.temperature = getOrDefault(temperature, 0.1);
        this.topP = getOrDefault(topP, 1.0);
        this.seed = getOrDefault(seed, 12345);
        this.maxTokens = getOrDefault(maxTokens, 512);
        this.onGPU = getOrDefault(onGPU, Boolean.TRUE);
    }

    public synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        GPULlama3ModelRegistry registry = GPULlama3ModelRegistry.getOrCreate(modelCachePath);
        try {
            Path modelPath = registry.downloadModel(modelName, quantization, Optional.empty(), Optional.empty());

            LOG.info("GPULlama3 model initialization {modelPath=" + modelPath
                    + ", temperature=" + temperature
                    + ", topP=" + topP
                    + ", seed=" + seed
                    + ", maxTokens=" + maxTokens
                    + ", onGPU=" + onGPU + "}...");

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
