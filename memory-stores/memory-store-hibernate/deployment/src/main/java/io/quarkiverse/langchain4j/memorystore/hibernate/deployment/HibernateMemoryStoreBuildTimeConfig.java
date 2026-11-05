package io.quarkiverse.langchain4j.memorystore.hibernate.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.memorystore.hibernate")
public interface HibernateMemoryStoreBuildTimeConfig {
    /**
     * The Quarkus Hibernate ORM persistence unit to use. If unset, uses the default persistence unit.
     * The chat memory entity is registered only in this unit.
     */
    Optional<String> persistenceUnitName();
}
