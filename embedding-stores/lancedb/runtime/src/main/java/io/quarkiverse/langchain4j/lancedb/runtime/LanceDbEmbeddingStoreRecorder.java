package io.quarkiverse.langchain4j.lancedb.runtime;

import java.util.function.Function;

import org.lance.namespace.LanceNamespace;

import com.lancedb.LanceDbNamespaceClientBuilder;

import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class LanceDbEmbeddingStoreRecorder {

    private final RuntimeValue<LanceDbEmbeddingStoreConfig> runtimeConfig;

    public LanceDbEmbeddingStoreRecorder(RuntimeValue<LanceDbEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<LanceDbEmbeddingStore>, LanceDbEmbeddingStore> embeddingStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public LanceDbEmbeddingStore apply(SyntheticCreationalContext<LanceDbEmbeddingStore> context) {
                LanceDbStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                String apiKey = storeConfig.apiKey().orElseThrow(
                        () -> new ConfigValidationException(createApiKeyConfigProblems(storeName)));
                String database = storeConfig.database().orElseThrow(
                        () -> new ConfigValidationException(createDatabaseConfigProblems(storeName)));

                if (storeConfig.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }

                LanceDbNamespaceClientBuilder builder = LanceDbNamespaceClientBuilder.newBuilder()
                        .apiKey(apiKey)
                        .database(database)
                        .region(storeConfig.region());
                storeConfig.endpoint().ifPresent(builder::endpoint);
                LanceNamespace namespace = builder.build();

                return new LanceDbEmbeddingStore(namespace, storeConfig.tableName(),
                        storeConfig.dimension().get(), storeConfig.distanceType());
            }
        };
    }

    private LanceDbStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createApiKeyConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.lancedb%sapi-key is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }

    private ConfigValidationException.Problem[] createDatabaseConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.lancedb%sdatabase is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.lancedb%sdimension is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + ".")))
        };
    }
}
