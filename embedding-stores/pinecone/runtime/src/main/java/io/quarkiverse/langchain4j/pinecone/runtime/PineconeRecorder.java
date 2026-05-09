package io.quarkiverse.langchain4j.pinecone.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.quarkiverse.langchain4j.pinecone.PineconeEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class PineconeRecorder {
    private final RuntimeValue<PineconeConfig> runtimeConfig;

    public PineconeRecorder(RuntimeValue<PineconeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<PineconeEmbeddingStore>, PineconeEmbeddingStore> embeddingStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public PineconeEmbeddingStore apply(SyntheticCreationalContext<PineconeEmbeddingStore> context) {
                PineconeStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                List<ConfigValidationException.Problem> problems = new ArrayList<>();
                String configPrefix = NamedConfigUtil.isDefault(storeName) ? "quarkus.langchain4j.pinecone."
                        : "quarkus.langchain4j.pinecone." + storeName + ".";

                String apiKey = storeConfig.apiKey().orElseGet(() -> {
                    problems.add(createConfigProblem("api-key", configPrefix));
                    return null;
                });
                String environment = storeConfig.environment().orElseGet(() -> {
                    problems.add(createConfigProblem("environment", configPrefix));
                    return null;
                });
                String projectId = storeConfig.projectId().orElseGet(() -> {
                    problems.add(createConfigProblem("project-id", configPrefix));
                    return null;
                });
                String indexName = storeConfig.indexName().orElseGet(() -> {
                    problems.add(createConfigProblem("index-name", configPrefix));
                    return null;
                });

                if (!problems.isEmpty()) {
                    throw new ConfigValidationException(problems.toArray(new ConfigValidationException.Problem[0]));
                }

                return new PineconeEmbeddingStore(apiKey,
                        indexName,
                        projectId,
                        environment,
                        storeConfig.namespace().orElse(null),
                        storeConfig.textFieldName(),
                        storeConfig.timeout().orElse(Duration.ofSeconds(5)),
                        storeConfig.dimension().orElse(null),
                        storeConfig.podType(),
                        storeConfig.indexReadinessTimeout().orElse(Duration.ofMinutes(1)));
            }
        };
    }

    private PineconeStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        } else {
            return runtimeConfig.getValue().namedConfig().get(storeName);
        }
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configPrefix) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property " + configPrefix + key
                        + " is required but it could not be found in any config source");
    }
}
