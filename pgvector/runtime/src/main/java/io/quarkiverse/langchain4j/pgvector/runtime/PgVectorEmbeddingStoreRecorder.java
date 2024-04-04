package io.quarkiverse.langchain4j.pgvector.runtime;

import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.pgvector.QuarkusPgVectorEmbeddingStore;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PgVectorEmbeddingStoreRecorder {

    public Function<SyntheticCreationalContext<QuarkusPgVectorEmbeddingStore>, QuarkusPgVectorEmbeddingStore> embeddingStoreFunction(
            PgVectorEmbeddingStoreConfig config, String datasourceName) {
        return context -> {
            AgroalDataSource dataSource = Optional.ofNullable(datasourceName)
                    .map(DataSourceLiteral::new)
                    .map(dl -> context.getInjectedReference(AgroalDataSource.class, dl))
                    .orElse(context.getInjectedReference(AgroalDataSource.class, new Default.Literal()));

            return new QuarkusPgVectorEmbeddingStore(dataSource, config.table(), config.dimension(), config.useIndex(),
                    config.indexListSize(), config.createTable(), config.dropTableFirst(), config.metadata());
        };
    }
}
