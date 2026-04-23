package io.quarkiverse.langchain4j.hibernate.deployment;

import java.util.Optional;

import io.quarkus.builder.item.MultiBuildItem;

public final class HibernateEmbeddingStoreMappingBuildItem extends MultiBuildItem {
    private String persistenceUnitName;
    private String entityName;

    public HibernateEmbeddingStoreMappingBuildItem(String persistenceUnitName, String entityName) {
        this.persistenceUnitName = persistenceUnitName;
        this.entityName = entityName;
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
}
