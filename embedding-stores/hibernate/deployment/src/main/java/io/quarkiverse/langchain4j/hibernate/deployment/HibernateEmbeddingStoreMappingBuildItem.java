package io.quarkiverse.langchain4j.hibernate.deployment;

import java.util.Map;
import java.util.Optional;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.builder.item.MultiBuildItem;

public final class HibernateEmbeddingStoreMappingBuildItem extends MultiBuildItem {
    private String embeddingStoreName;
    private String databaseKind;
    private String persistenceUnitName;
    private String entityName;
    private DistanceFunction distanceFunction;
    private Map<String, Object> queryHints;

    public HibernateEmbeddingStoreMappingBuildItem(String embeddingStoreName, String databaseKind,
            String persistenceUnitName, String entityName, DistanceFunction distanceFunction,
            Map<String, Object> queryHints) {
        this.embeddingStoreName = embeddingStoreName;
        this.databaseKind = databaseKind;
        this.persistenceUnitName = persistenceUnitName;
        this.entityName = entityName;
        this.distanceFunction = distanceFunction;
        this.queryHints = queryHints;
    }

    public String getEmbeddingStoreName() {
        return embeddingStoreName;
    }

    public void setEmbeddingStoreName(String embeddingStoreName) {
        this.embeddingStoreName = embeddingStoreName;
    }

    public String getDatabaseKind() {
        return databaseKind;
    }

    public void setDatabaseKind(String databaseKind) {
        this.databaseKind = databaseKind;
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

    public Map<String, Object> getQueryHints() {
        return queryHints;
    }

    public void setQueryHints(Map<String, Object> queryHints) {
        this.queryHints = queryHints;
    }
}
