package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.pgvector.PgVectorAgroalPoolInterceptor;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class PgVectorEmbeddingStoreRecorder {
    private final RuntimeValue<PgVectorEmbeddingStoreConfig> runtimeConfig;

    public PgVectorEmbeddingStoreRecorder(RuntimeValue<PgVectorEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<PgVectorEmbeddingStore>, PgVectorEmbeddingStore> embeddingStoreFunction(
            String datasourceName, String storeName) {
        return new Function<>() {
            @Override
            public PgVectorEmbeddingStore apply(SyntheticCreationalContext<PgVectorEmbeddingStore> context) {
                AgroalDataSource dataSource;
                if (datasourceName != null && !NamedConfigUtil.isDefault(datasourceName)) {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, new DataSourceLiteral(datasourceName));
                } else {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, Default.Literal.INSTANCE);
                }

                dataSource.flush(AgroalDataSource.FlushMode.GRACEFUL);

                PgVectorStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                if (storeConfig.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }

                return new PgVectorEmbeddingStore(dataSource,
                        storeConfig.table(),
                        storeConfig.dimension().get(),
                        storeConfig.useIndex(),
                        storeConfig.indexListSize(),
                        storeConfig.createTable(),
                        storeConfig.dropTableFirst(),
                        storeConfig.metadata());
            }
        };
    }

    private PgVectorStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        PgVectorStoreRuntimeConfig storeConfig;
        if (NamedConfigUtil.isDefault(storeName)) {
            storeConfig = runtimeConfig.getValue().defaultConfig();
        } else {
            storeConfig = runtimeConfig.getValue().namedConfig().get(storeName);
        }
        return storeConfig;
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.pgvector%sdimension is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(storeName) ? "." : ("." + storeName + "."))) };
    }

    public Supplier<PgVectorAgroalPoolInterceptor> pgVectorAgroalPoolInterceptor(String storeName) {
        return new Supplier<>() {
            @Override
            public PgVectorAgroalPoolInterceptor get() {
                return new PgVectorAgroalPoolInterceptor(correspondingStoreConfig(storeName));
            }
        };
    }
}
