package io.quarkiverse.langchain4j.hibernate.milvus.runtime;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MilvusHibernateEmbeddingStoreRecorder {

    public RuntimeValue<DatabaseKind> milvusDatabaseKind() {
        return new RuntimeValue<>(MilvusDatabaseKind.INSTANCE);
    }
}