package io.quarkiverse.langchain4j.hibernate.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.hibernate")
public interface HibernateEmbeddingStoreConfig {

    /**
     * The distance function to use.
     */
    @WithDefault("COSINE")
    DistanceFunction distanceFunction();

    /**
     * Whether the vector configuration of the database should be setup e.g. the PG extension should be created on Start.
     * By Default, if it's dev or test environment the value is overridden to true
     */
    @WithDefault("false")
    Boolean setupVectorConfig();

}
