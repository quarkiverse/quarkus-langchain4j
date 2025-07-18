package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.pgvector.PgVectorAgroalPoolInterceptor;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PgVectorEmbeddingStoreRecorder {
    private final RuntimeValue<PgVectorEmbeddingStoreConfig> runtimeConfig;

    public PgVectorEmbeddingStoreRecorder(RuntimeValue<PgVectorEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<PgVectorEmbeddingStore>, PgVectorEmbeddingStore> embeddingStoreFunction(
            String datasourceName) {
        return new Function<>() {
            @Override
            public PgVectorEmbeddingStore apply(SyntheticCreationalContext<PgVectorEmbeddingStore> context) {
                AgroalDataSource dataSource;
                if (datasourceName != null) {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, new DataSourceLiteral(datasourceName));
                } else {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, Default.Literal.INSTANCE);
                }

                dataSource.flush(AgroalDataSource.FlushMode.GRACEFUL);

                return new PgVectorEmbeddingStore(dataSource,
                        runtimeConfig.getValue().table(),
                        runtimeConfig.getValue().dimension(),
                        runtimeConfig.getValue().useIndex(),
                        runtimeConfig.getValue().indexListSize(),
                        runtimeConfig.getValue().createTable(),
                        runtimeConfig.getValue().dropTableFirst(),
                        runtimeConfig.getValue().metadata());
            }
        };
    }

    public Supplier<PgVectorAgroalPoolInterceptor> pgVectorAgroalPoolInterceptor() {
        return new Supplier<>() {
            @Override
            public PgVectorAgroalPoolInterceptor get() {
                return new PgVectorAgroalPoolInterceptor(runtimeConfig.getValue());
            }
        };
    }
}
