package io.quarkiverse.langchain4j.hibernate.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.hibernate-orm")
public interface HibernateEmbeddingStoreBuildTimeConfig {

    /**
     * The name of the entity of the configured Hibernate persistence unit to use for this store.
     * If not set and the persistence unit has exactly one entity with `@Embedding`, that entity is used.
     */
    Optional<String> entityName();

    /**
     * The name of the configured Hibernate persistence unit to use for this store. If not set,
     * the default persistence unit name from the Hibernate extension will be used.
     */
    Optional<String> persistenceUnit();

}
