package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.pgvector.PgVectorAgroalPoolInterceptor;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PgVectorEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<PgVectorEmbeddingStore>, PgVectorEmbeddingStore> embeddingStoreFunction(
            PgVectorEmbeddingStoreConfig config, String datasourceName) {
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

                return new PgVectorEmbeddingStore(dataSource, config.table(), config.dimension(), config.useIndex(),
                        config.indexListSize(), config.createTable(), config.dropTableFirst(), config.metadata());
            }
        };
    }

    public Supplier<PgVectorAgroalPoolInterceptor> pgVectorAgroalPoolInterceptor(PgVectorEmbeddingStoreConfig config) {
        return new Supplier<>() {
            @Override
            public PgVectorAgroalPoolInterceptor get() {
                return new PgVectorAgroalPoolInterceptor(config);
            }
        };
    }
}
