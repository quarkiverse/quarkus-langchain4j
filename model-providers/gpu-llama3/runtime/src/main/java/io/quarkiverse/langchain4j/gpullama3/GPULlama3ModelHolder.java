package io.quarkiverse.langchain4j.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.beehive.jitllm.api.GenerationSession;
import org.beehive.jitllm.api.LocalModel;
import org.beehive.jitllm.api.LocalModels;
import org.beehive.jitllm.api.ModelCapabilities;
import org.beehive.jitllm.api.ModelOptions;
import org.beehive.jitllm.api.SessionOptions;
import org.beehive.jitllm.api.TextGenerationModel;
import org.beehive.jitllm.api.ThinkingMode;
import org.beehive.jitllm.runtime.backend.BackendId;
import org.beehive.jitllm.runtime.policy.ExecutionPolicy;
import org.jboss.logging.Logger;

/**
 * Holds a single loaded GPULlama3 model, shared between {@link GPULlama3ChatModel} and
 * {@link GPULlama3StreamingChatModel} for the same configuration, so the weights are loaded into
 * device memory once.
 *
 * <p>
 * <b>This is the owner.</b> It loads the model, opens the one session both beans generate
 * through, and closes both exactly once at application shutdown. Requests do not own it: a request
 * borrows the session and closing it is not a request's business. Before the façade migration
 * nothing closed the TornadoVM plan at all — it leaked until the JVM exited.
 *
 * <p>
 * Migrated to the engine's public façade (GPULlama3 T12.9b). It previously reached into
 * {@code ModelLoader}, {@code State}, {@code Sampler}, {@code ChatFormat} and
 * {@code TornadoVMMasterPlan}; it now holds a {@link LocalModel} and a {@link GenerationSession}.
 */
public class GPULlama3ModelHolder implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GPULlama3ModelHolder.class);

    private final Optional<Path> modelCachePath;
    private final String modelName;
    private final String quantization;
    final double temperature;
    final double topP;
    final int seed;
    final int maxTokens;
    final boolean onGPU;
    final boolean withPrefillDecode;
    final int prefillBatchSize;
    final boolean enableThinking;
    private final String deviceMemory;

    // force happens-before relationship between initialization and usage
    private volatile boolean initialized = false;

    LocalModel model;

    /**
     * The one session both beans generate through, opened at initialization.
     *
     * <p>
     * Not one per request: on the accelerator path each session builds its own execution plan
     * holding its own device copy of the weights, so a session per request exhausts device memory.
     */
    GenerationSession session;

    /** What the resolved reasoning mode turned out to be, for diagnostics. */
    ThinkingMode resolvedThinkingMode = ThinkingMode.DEFAULT;

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
            Integer prefillBatchSize,
            Boolean enableThinking,
            String deviceMemory) {
        DefaultConfig defaultConfig = defaultConfigForModel(modelName);
        this.modelCachePath = modelCachePath;
        this.modelName = modelName;
        this.quantization = quantization;
        this.temperature = getOrDefault(temperature, defaultConfig.temperature());
        this.topP = getOrDefault(topP, defaultConfig.topP());
        this.seed = getOrDefault(seed, ThreadLocalRandom.current().nextInt());
        this.maxTokens = getOrDefault(maxTokens, defaultConfig.maxTokens());
        this.onGPU = getOrDefault(onGPU, Boolean.TRUE);
        this.withPrefillDecode = getOrDefault(withPrefillDecode, Boolean.FALSE);
        this.prefillBatchSize = getOrDefault(prefillBatchSize, 1);
        this.enableThinking = getOrDefault(enableThinking, Boolean.FALSE);
        this.deviceMemory = getOrDefault(deviceMemory, "4GB");
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

            // Prefill/decode is an execution policy on ModelOptions now, not a JVM-global flag.
            // The device memory budget is still TornadoVM's own property.
            System.setProperty("tornado.device.memory", deviceMemory);

            LOG.info("GPULlama3 model initialization {modelPath=" + modelPath
                    + ", temperature=" + temperature
                    + ", topP=" + topP
                    + ", seed=" + seed
                    + ", maxTokens=" + maxTokens
                    + ", onGPU=" + onGPU
                    + ", withPrefillDecode=" + withPrefillDecode
                    + ", prefillBatchSize=" + prefillBatchSize
                    + ", enableThinking=" + enableThinking
                    + ", deviceMemory=" + deviceMemory + "}...");

            ModelOptions.Builder options = ModelOptions.builder()
                    .contextLength(maxTokens);
            if (onGPU) {
                // No backend is named: the engine resolves whichever one the installed
                // TornadoVM SDK provides. Naming CUDA here made the extension refuse to start
                // on an OpenCL or Metal SDK, since an explicit backend that disagrees with the
                // resolved device is rejected rather than silently substituted.
                System.setProperty("use.tornadovm", "true");
            } else {
                options.backend(BackendId.CPU);
            }
            if (withPrefillDecode) {
                options.executionPolicy(ExecutionPolicy.builder()
                        .phaseStrategy(ExecutionPolicy.PhaseStrategy.PREFILL_DECODE)
                        .prefillBatchSize(prefillBatchSize)
                        .build());
            }
            this.model = LocalModels.load(modelPath, options.build());
            this.session = openSession((TextGenerationModel) model);

            initialized = true;
            LOG.infof("GPULlama3 model initialization complete! (%s: toolCalling=%s, thinkingControl=%s)",
                    model.info().name(), capabilities().toolCalling(), capabilities().thinkingControl());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** The configured model name, as the configuration spelled it. */
    String modelName() {
        return modelName;
    }

    /**
     * What the loaded model's chat format supports, as the engine reports it. Answers only once
     * the model is loaded; the answer cannot change for a loaded model.
     */
    ModelCapabilities capabilities() {
        ensureInitialized();
        return model.info().capabilities();
    }

    /**
     * Opens the session, honouring {@code enable-thinking} where the family has a reasoning phase.
     *
     * <p>
     * The façade <b>rejects</b> an explicit reasoning mode on a family that has no reasoning
     * phase, deliberately: a caller who turned thinking off and silently got it anyway pays for
     * tokens they asked not to generate. This extension's property, though, has always been a no-op
     * on such families rather than an error, and that published behaviour is preserved — the
     * explicit mode is attempted once, at initialization, and a family that cannot represent it
     * falls back to the default with the same message this class already logged.
     *
     * <p>
     * Once, not per request: this is a capability probe, and the answer cannot change for a
     * loaded model.
     */
    private GenerationSession openSession(TextGenerationModel generation) {
        ThinkingMode requested = enableThinking ? ThinkingMode.ENABLED : ThinkingMode.DISABLED;
        try {
            GenerationSession opened = generation.newSession(
                    SessionOptions.builder().thinkingMode(requested).build());
            this.resolvedThinkingMode = requested;
            return opened;
        } catch (IllegalArgumentException notControllable) {
            LOG.debugf("Thinking control not applicable for this model; enable-thinking=%s has no"
                    + " effect (%s).", enableThinking, notControllable.getMessage());
            this.resolvedThinkingMode = ThinkingMode.DEFAULT;
            return generation.newSession();
        }
    }

    /**
     * Closes the session and the model, exactly once, at application shutdown.
     *
     * <p>
     * In that order: the engine refuses to close a model with a live session, which is the
     * ownership rule made enforceable rather than documented.
     */
    @Override
    public synchronized void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        initialized = false;
    }
}
