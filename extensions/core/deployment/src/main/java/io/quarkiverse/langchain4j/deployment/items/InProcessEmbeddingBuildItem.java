package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class InProcessEmbeddingBuildItem extends MultiBuildItem implements ProviderHolder {

    private final String modelName;
    private final String onnxModelPath;
    private final String vocabularyPath;

    private final String className;

    public InProcessEmbeddingBuildItem(String modelName, String className, String onnxModelPath, String vocabularyPath) {
        this.modelName = modelName;
        this.className = className;
        this.onnxModelPath = onnxModelPath;
        this.vocabularyPath = vocabularyPath;
    }

    public String modelName() {
        return modelName;
    }

    public String onnxModelPath() {
        return onnxModelPath;
    }

    public String vocabularyPath() {
        return vocabularyPath;
    }

    public String className() {
        return className;
    }

    @Override
    public String getProvider() {
        return className;
    }
}
