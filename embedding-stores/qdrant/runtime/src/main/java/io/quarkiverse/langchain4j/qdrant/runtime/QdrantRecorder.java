package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.function.Function;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class QdrantRecorder {
    private final RuntimeValue<QdrantEmbeddingStoreConfig> runtimeConfig;

    public QdrantRecorder(RuntimeValue<QdrantEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<QdrantEmbeddingStore>, QdrantEmbeddingStore> qdrantStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public QdrantEmbeddingStore apply(SyntheticCreationalContext<QdrantEmbeddingStore> context) {
                QdrantStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                String host = storeConfig.host()
                        .orElseGet(() -> runtimeConfig.getValue().defaultConfig().host().orElseThrow(
                                () -> new ConfigValidationException(createHostConfigProblems(storeName))));

                return new QdrantEmbeddingStore.Builder()
                        .host(host)
                        .port(storeConfig.port())
                        .apiKey(storeConfig.apiKey().orElse(null))
                        .collectionName(storeConfig.collection().name())
                        .useTls(storeConfig.useTls())
                        .payloadTextKey(storeConfig.payloadTextKey())
                        .build();
            }
        };
    }

    private QdrantStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        QdrantStoreRuntimeConfig namedConfig = runtimeConfig.getValue().namedConfig().get(storeName);
        if (namedConfig != null) {
            return namedConfig;
        }
        return runtimeConfig.getValue().defaultConfig();
    }

    private ConfigValidationException.Problem[] createHostConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.qdrant%shost is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }
}
