package io.quarkiverse.langchain4j.chroma.runtime;

import java.time.Duration;
import java.util.function.Function;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class ChromaRecorder {
    private final RuntimeValue<ChromaEmbeddingStoreConfig> runtimeConfig;

    public ChromaRecorder(RuntimeValue<ChromaEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChromaEmbeddingStore>, ChromaEmbeddingStore> chromaStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public ChromaEmbeddingStore apply(SyntheticCreationalContext<ChromaEmbeddingStore> context) {
                ChromaStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                String url = storeConfig.url()
                        .orElseGet(() -> runtimeConfig.getValue().defaultConfig().url().orElseThrow(
                                () -> new ConfigValidationException(createUrlConfigProblems(storeName))));

                ChromaEmbeddingStore.Builder builder = ChromaEmbeddingStore.builder();
                builder.apiVersion(storeConfig.apiVersion());
                builder.collectionName(storeConfig.collectionName());
                builder.logRequests(storeConfig.logRequests().orElse(false));
                builder.logResponses(storeConfig.logResponses().orElse(false));
                builder.timeout(storeConfig.timeout().orElse(Duration.ofSeconds(5)));
                builder.baseUrl(url);
                return builder.build();
            }
        };
    }

    private ChromaStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createUrlConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.chroma%surl is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }
}
