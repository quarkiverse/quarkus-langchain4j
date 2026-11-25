package io.quarkiverse.langchain4j.hibernate.deployment;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;

public final class HibernateEmbeddingStoreDatabaseKindMappingBuildItem extends MultiBuildItem {
    private final String databaseKindName;
    private final DatabaseKind databaseKind;
    private final RuntimeValue<DatabaseKind> databaseKindRuntimeValue;

    public HibernateEmbeddingStoreDatabaseKindMappingBuildItem(String databaseKindName,
            DatabaseKind databaseKind, RuntimeValue<DatabaseKind> databaseKindRuntimeValue) {
        this.databaseKindName = databaseKindName;
        this.databaseKind = databaseKind;
        this.databaseKindRuntimeValue = databaseKindRuntimeValue;
    }

    public String getDatabaseKindName() {
        return databaseKindName;
    }

    public DatabaseKind getDatabaseKind() {
        return databaseKind;
    }

    public RuntimeValue<DatabaseKind> getDatabaseKindRuntimeValue() {
        return databaseKindRuntimeValue;
    }

    @Override
    public String toString() {
        return databaseKindName;
    }
}