package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import io.quarkiverse.langchain4j.hibernate.deployment.HibernateEmbeddingStoreDatabaseKindMappingBuildItem;
import io.quarkiverse.langchain4j.hibernate.milvus.runtime.MilvusHibernateEmbeddingStoreRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.hibernate.orm.deployment.spi.DatabaseKindDialectBuildItem;

public final class HibernateNoSQLMilvusProcessor {

    private static final String DIALECT = "org.hibernate.milvus.MilvusDialect";

    @BuildStep
    DatabaseKindDialectBuildItem databaseKindDialect() {
        return DatabaseKindDialectBuildItem.forThirdPartyDialect(JDBCMilvusProcessor.DATABASE_KIND, DIALECT);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void produceDatabaseKindMappings(
            MilvusHibernateEmbeddingStoreRecorder recorder,
            BuildProducer<HibernateEmbeddingStoreDatabaseKindMappingBuildItem> databaseKindMappingProducer) {
        databaseKindMappingProducer.produce(new HibernateEmbeddingStoreDatabaseKindMappingBuildItem(
                JDBCMilvusProcessor.DATABASE_KIND, MilvusDatabaseKind.INSTANCE, recorder.milvusDatabaseKind()));
    }
}