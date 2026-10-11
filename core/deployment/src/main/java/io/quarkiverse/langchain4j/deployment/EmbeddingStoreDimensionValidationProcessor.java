package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import io.quarkiverse.langchain4j.runtime.embeddingstore.EmbeddingStoreDimensionConfig;
import io.quarkiverse.langchain4j.runtime.embeddingstore.EmbeddingStoreDimensionValidationRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class EmbeddingStoreDimensionValidationProcessor {

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void validateEmbeddingStoreDimensions(List<EmbeddingStoreBuildItem> embeddingStores,
            EmbeddingStoreDimensionValidationRecorder recorder) {
        List<EmbeddingStoreDimensionConfig> stores = embeddingStores.stream()
                .filter(item -> item.getDimensionConfigProperty() != null || item.getDimension() != null)
                .map(item -> new EmbeddingStoreDimensionConfig(item.getStoreName(), item.getDimensionConfigProperty(),
                        item.getDimension()))
                .toList();
        if (!stores.isEmpty()) {
            recorder.validateDimensions(stores);
        }
    }
}
