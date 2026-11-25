package io.quarkiverse.langchain4j.hibernate.deployment;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import io.quarkus.builder.item.MultiBuildItem;

public final class HibernateEmbeddingStoreMappingBuildItem extends MultiBuildItem {
    private String persistenceUnitName;
    private String entityName;
    private DistanceFunction distanceFunction;
    private String storeName;

    public HibernateEmbeddingStoreMappingBuildItem(String persistenceUnitName, String entityName, String storeName) {
        this.persistenceUnitName = persistenceUnitName;
        this.entityName = entityName;
        this.storeName = storeName;
    }

    public HibernateEmbeddingStoreMappingBuildItem(String persistenceUnitName, String entityName, String storeName,
            DistanceFunction distanceFunction) {
        this(persistenceUnitName, entityName, storeName);
        this.distanceFunction = distanceFunction;
    }

    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    public String getEntityName() {
        return entityName;
    }

    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }

    public String getStoreName() {
        return storeName;
    }
}
