package io.quarkiverse.langchain4j.neo4j.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class Neo4jEmbeddingStoreRecorder {
    private final RuntimeValue<Neo4jEmbeddingStoreConfig> runtimeConfig;

    public Neo4jEmbeddingStoreRecorder(RuntimeValue<Neo4jEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<Neo4jEmbeddingStore>, Neo4jEmbeddingStore> embeddingStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public Neo4jEmbeddingStore apply(SyntheticCreationalContext<Neo4jEmbeddingStore> context) {
                Neo4jStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                if (storeConfig.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }

                Driver driver = context.getInjectedReference(Driver.class, Default.Literal.INSTANCE);

                var builder = Neo4jEmbeddingStore.builder();
                builder.driver(driver);
                builder.dimension(storeConfig.dimension().get());
                builder.label(storeConfig.label());
                builder.embeddingProperty(storeConfig.embeddingProperty());
                builder.idProperty(storeConfig.idProperty());
                builder.metadataPrefix(storeConfig.metadataPrefix().orElse(""));
                builder.textProperty(storeConfig.textProperty());
                builder.indexName(storeConfig.indexName());
                builder.databaseName(storeConfig.databaseName());
                builder.retrievalQuery(storeConfig.retrievalQuery().orElseGet(() -> defaultRetrievalQuery(storeConfig)));
                builder.config(SessionConfig.forDatabase(storeConfig.databaseName()));
                return builder.build();
            }
        };
    }

    private Neo4jStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private static String defaultRetrievalQuery(Neo4jStoreRuntimeConfig storeConfig) {
        return String.format(
                "RETURN properties(node) AS metadata, node.%1$s AS %1$s, node.%2$s AS %2$s, node.%3$s AS %3$s, score",
                storeConfig.idProperty(),
                storeConfig.textProperty(),
                storeConfig.embeddingProperty());
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.neo4j%sdimension is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + "."))) };
    }
}
