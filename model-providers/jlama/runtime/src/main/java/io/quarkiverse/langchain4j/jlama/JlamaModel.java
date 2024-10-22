package io.quarkiverse.langchain4j.jlama;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.Function;
import com.github.tjake.jlama.safetensors.prompt.Tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.output.FinishReason;

/**
 * A Jlama model. Very basic information. Allows the model to be loaded with different options.
 */
public class JlamaModel {
    private final JlamaModelRegistry registry;

    private final ModelSupport.ModelType modelType;
    private final String modelName;
    private final Optional<String> owner;
    private final String modelId;
    private final boolean isLocal;

    JlamaModel(JlamaModelRegistry registry, ModelSupport.ModelType modelType, String modelName, Optional<String> owner,
            String modelId, boolean isLocal) {
        this.registry = registry;
        this.modelType = modelType;
        this.modelName = modelName;
        this.owner = owner;
        this.modelId = modelId;
        this.isLocal = isLocal;
    }

    ModelSupport.ModelType getModelType() {
        return modelType;
    }

    String getModelName() {
        return modelName;
    }

    Optional<String> getOwner() {
        return owner;
    }

    String getModelId() {
        return modelId;
    }

    boolean isLocal() {
        return isLocal;
    }

    Loader loader() {
        return new Loader(registry, modelName);
    }

    static class Loader {
        private final JlamaModelRegistry registry;
        private final String modelName;

        private Path workingDirectory;
        private DType workingQuantizationType = DType.I8;
        private DType quantizationType;
        private Integer threadCount;
        private AbstractModel.InferenceType inferenceType = AbstractModel.InferenceType.FULL_GENERATION;

        private Loader(JlamaModelRegistry registry, String modelName) {
            this.registry = registry;
            this.modelName = modelName;
        }

        public Loader quantized() {
            //For now only allow Q4 quantization at runtime
            this.quantizationType = DType.Q4;
            return this;
        }

        /**
         * Set the working quantization type. This is the type that the model will use for working inference memory.
         */
        public Loader workingQuantizationType(DType workingQuantizationType) {
            this.workingQuantizationType = workingQuantizationType;
            return this;
        }

        public Loader workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Loader threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Loader inferenceType(AbstractModel.InferenceType inferenceType) {
            this.inferenceType = inferenceType;
            return this;
        }

        public AbstractModel load() {
            return ModelSupport.loadModel(
                    inferenceType,
                    new File(registry.getModelCachePath().toFile(), modelName),
                    workingDirectory == null ? null : workingDirectory.toFile(),
                    DType.F32,
                    workingQuantizationType,
                    Optional.ofNullable(quantizationType),
                    Optional.ofNullable(threadCount),
                    Optional.empty(),
                    SafeTensorSupport::loadWeights);
        }
    }

    static Tool toTool(ToolSpecification toolSpecification) {
        Function.Builder builder = Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description());

        for (Map.Entry<String, Map<String, Object>> p : toolSpecification.parameters().properties().entrySet()) {
            builder.addParameter(p.getKey(), p.getValue(), toolSpecification.parameters().required().contains(p.getKey()));
        }

        return Tool.from(builder.build());
    }

    static FinishReason toFinishReason(Generator.FinishReason reason) {
        return switch (reason) {
            case STOP_TOKEN -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case ERROR -> FinishReason.OTHER;
            case TOOL_CALL -> FinishReason.TOOL_EXECUTION;
            default -> throw new IllegalArgumentException("Unknown reason: " + reason);
        };
    }
}
