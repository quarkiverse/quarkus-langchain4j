package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
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
                if (datasourceName == null) {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, new DataSourceLiteral(datasourceName));
                }
                return new PgVectorEmbeddingStore(dataSource, config.table(), config.dimension(), config.useIndex(),
                        config.indexListSize(), config.createTable(), config.dropTableFirst());
            }
        };
    }

}
