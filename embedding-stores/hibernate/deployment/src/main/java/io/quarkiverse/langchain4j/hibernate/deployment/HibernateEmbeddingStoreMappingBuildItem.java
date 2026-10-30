package io.quarkiverse.langchain4j.hibernate.deployment;

import java.util.Optional;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.builder.item.MultiBuildItem;

public final class HibernateEmbeddingStoreMappingBuildItem extends MultiBuildItem {
    private String persistenceUnitName;
    private String entityName;
    private DistanceFunction distanceFunction;

    public HibernateEmbeddingStoreMappingBuildItem(String persistenceUnitName, String entityName,
            DistanceFunction distanceFunction) {
        this.persistenceUnitName = persistenceUnitName;
        this.entityName = entityName;
        this.distanceFunction = distanceFunction;
    }

    public Optional<String> getPersistenceUnitName() {
        return Optional.ofNullable(persistenceUnitName);
    }

    public void setPersistenceUnitName(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    public Optional<String> getEntityName() {
        return Optional.ofNullable(entityName);
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }

    public void setDistanceFunction(DistanceFunction distanceFunction) {
        this.distanceFunction = distanceFunction;
    }
}
