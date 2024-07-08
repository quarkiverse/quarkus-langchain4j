package io.quarkiverse.langchain4j.pgvector.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.pgvector")
public interface PgVectorEmbeddingStoreBuildTimeConfig {

    /**
     * The name of the configured Postgres datasource to use for this store. If not
     * set, the default datasource from the Agroal extension will be used.
     */
    Optional<String> datasource();

}
