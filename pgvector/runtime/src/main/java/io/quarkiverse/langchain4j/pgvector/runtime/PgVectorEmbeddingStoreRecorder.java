package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PgVectorEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<PgVectorEmbeddingStore>, PgVectorEmbeddingStore> embeddingStoreFunction(
            PgVectorEmbeddingStoreConfig config) {
        return new Function<>() {
            @Override
            public PgVectorEmbeddingStore apply(SyntheticCreationalContext<PgVectorEmbeddingStore> context) {
                AgroalDataSource dataSource;
                //TODO handle named datasources
                dataSource = context.getInjectedReference(AgroalDataSource.class, new Default.Literal());
                return new PgVectorEmbeddingStore(dataSource, config.table(), config.dimension(), config.useIndex(),
                        config.indexListSize(), config.createTable(), config.dropTableFirst());
            }
        };
    }

}
