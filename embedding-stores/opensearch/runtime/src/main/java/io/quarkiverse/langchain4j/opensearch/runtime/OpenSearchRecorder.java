package io.quarkiverse.langchain4j.opensearch.runtime;

import java.util.function.Function;

import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenSearchRecorder {
    private final RuntimeValue<OpenSearchEmbeddingStoreConfig> runtimeConfig;

    public OpenSearchRecorder(RuntimeValue<OpenSearchEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<OpenSearchEmbeddingStore>, OpenSearchEmbeddingStore> openSearchStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public OpenSearchEmbeddingStore apply(SyntheticCreationalContext<OpenSearchEmbeddingStore> context) {
                OpenSearchStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                String serverUrl = storeConfig.serverUrl().orElseThrow(
                        () -> new ConfigValidationException(createServerUrlConfigProblems(storeName)));

                OpenSearchEmbeddingStore.Builder builder = OpenSearchEmbeddingStore.builder();
                builder.serverUrl(serverUrl);
                storeConfig.userName().ifPresent(builder::userName);
                storeConfig.password().ifPresent(builder::password);
                storeConfig.apiKey().ifPresent(builder::apiKey);
                builder.indexName(storeConfig.indexName());
                return builder.build();
            }
        };
    }

    private OpenSearchStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createServerUrlConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.opensearch%sserver-url is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }
}
