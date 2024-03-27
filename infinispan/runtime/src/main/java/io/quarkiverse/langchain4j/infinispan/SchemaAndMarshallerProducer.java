package io.quarkiverse.langchain4j.infinispan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.MessageMarshaller;
import org.infinispan.protostream.schema.Schema;

import dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration;
import dev.langchain4j.store.embedding.infinispan.LangChainItemMarshaller;
import dev.langchain4j.store.embedding.infinispan.LangChainMetadataMarshaller;
import dev.langchain4j.store.embedding.infinispan.LangchainSchemaCreator;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreConfig;

@ApplicationScoped
public class SchemaAndMarshallerProducer {

    @Inject
    private Instance<InfinispanEmbeddingStoreConfig> infinispanEmbeddingStoreConfigHandle;

    @Produces
    public FileDescriptorSource schemaDefinition() {
        InfinispanStoreConfiguration storeConfig = InfinispanEmbeddingStoreConfig.toStoreConfig(
                infinispanEmbeddingStoreConfigHandle.get());
        Schema schema = LangchainSchemaCreator.buildSchema(storeConfig);
        return FileDescriptorSource.fromString(storeConfig.fileName(), schema.toString());
    }

    @Produces
    public MessageMarshaller langchainItemMarshaller() {
        InfinispanStoreConfiguration storeConfig = InfinispanEmbeddingStoreConfig.toStoreConfig(
                infinispanEmbeddingStoreConfigHandle.get());
        return new LangChainItemMarshaller(storeConfig.langchainItemFullType());
    }

    @Produces
    public MessageMarshaller langchainMetadataMarshaller() {
        InfinispanStoreConfiguration storeConfig = InfinispanEmbeddingStoreConfig.toStoreConfig(
                infinispanEmbeddingStoreConfigHandle.get());
        return new LangChainMetadataMarshaller(storeConfig.metadataFullType());
    }
}
