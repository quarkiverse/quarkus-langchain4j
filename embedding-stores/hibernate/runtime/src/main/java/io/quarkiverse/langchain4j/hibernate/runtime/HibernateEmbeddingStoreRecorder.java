package io.quarkiverse.langchain4j.hibernate.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Default;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class HibernateEmbeddingStoreRecorder {
    private final RuntimeValue<HibernateEmbeddingStoreConfig> runtimeConfig;

    public HibernateEmbeddingStoreRecorder(RuntimeValue<HibernateEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<HibernateEmbeddingStore<?>>, HibernateEmbeddingStore<?>> embeddingStoreFunction(
            String databaseKind,
            String entityName,
            String embeddingAttributeName,
            String embeddedTextAttributeName,
            String unmappedMetadataAttributeName,
            String[] metadataAttributeNames,
            String persistenceUnitName) {
        return new Function<>() {
            @Override
            public HibernateEmbeddingStore<?> apply(SyntheticCreationalContext<HibernateEmbeddingStore<?>> context) {
                EntityManagerFactory entityManagerFactory;
                if (persistenceUnitName != null) {
                    entityManagerFactory = context.getInjectedReference(
                            EntityManagerFactory.class,
                            new PersistenceUnit.PersistenceUnitLiteral(persistenceUnitName));
                } else {
                    entityManagerFactory = context.getInjectedReference(
                            EntityManagerFactory.class,
                            Default.Literal.INSTANCE);
                }

                return HibernateEmbeddingStore.builder(entityManagerFactory.getMetamodel().entity(entityName).getJavaType())
                        .databaseKind(langchain4jDatabaseKind(databaseKind))
                        .sessionFactory(entityManagerFactory.unwrap(SessionFactory.class))
                        .embeddingAttributeName(embeddingAttributeName)
                        .embeddedTextAttributeName(embeddedTextAttributeName)
                        .unmappedMetadataAttributeName(unmappedMetadataAttributeName)
                        .metadataAttributeNames(metadataAttributeNames)
                        .distanceFunction(runtimeConfig.getValue().distanceFunction())
                        .build();
            }
        };
    }

    public Supplier<SetupVectorConfigAgroalPoolInterceptor> setupVectorConfigAgroalPoolInterceptor(String setupSql) {
        return new Supplier<>() {
            @Override
            public SetupVectorConfigAgroalPoolInterceptor get() {
                return new SetupVectorConfigAgroalPoolInterceptor(runtimeConfig.getValue(), setupSql);
            }
        };
    }

    public static DatabaseKind langchain4jDatabaseKind(String databaseKind) {
        return switch (databaseKind) {
            case "db2" -> DatabaseKind.DB2;
            case "mariadb" -> DatabaseKind.MARIADB;
            case "mssql" -> DatabaseKind.MSSQL;
            case "mysql" -> DatabaseKind.MYSQL;
            case "postgresql" -> DatabaseKind.POSTGRESQL;
            case "oracle" -> DatabaseKind.ORACLE;
            default -> null;
        };
    }
}
