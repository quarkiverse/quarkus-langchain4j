package io.quarkiverse.langchain4j.infinispan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.MessageMarshaller;

import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainItemMarshaller;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainMetadataMarshaller;

/**
 * Produces the Protobuf schema and marshallers needed by the Infinispan client
 * to store and retrieve embeddings. The schema defines the structure of the
 * embedding item and its metadata, including the vector field with configurable
 * dimension and similarity metric.
 */
@ApplicationScoped
public class SchemaAndMarshallerProducer {

    public static final String LANGCHAIN_ITEM = "LangchainItem";
    public static final String LANGCHAIN_METADATA = "LangchainMetadata";

    private static final String PROTO = "syntax = \"proto2\";\n" + "\n"
            + "/**\n" + " * @Indexed\n" + " */\n"
            + "message LangchainMetadataDIMENSION {\n"
            + "   \n"
            + "   /**\n" + "    * @Basic(projectable=true)\n" + "    */\n"
            + "   optional string name = 1;\n"
            + "   \n"
            + "   /**\n" + "    * @Basic(projectable=true)\n" + "    */\n"
            + "   optional string value = 2;\n"
            + "   \n"
            + "   /**\n" + "    * @Basic(projectable=true)\n" + "    */\n"
            + "   optional int64 value_int = 3;\n"
            + "   \n"
            + "   /**\n" + "    * @Basic(projectable=true)\n" + "    */\n"
            + "   optional double value_float = 4;\n"
            + "}\n"
            + "\n"
            + "/**\n" + " * @Indexed\n" + " */\n"
            + "message LangchainItemDIMENSION {\n"
            + "   \n"
            + "   /**\n" + "    * @Keyword\n" + "    */\n"
            + "   optional string id = 1;\n"
            + "   \n"
            + "   /**\n" + "    * @Vector(dimension=DIMENSION, similarity=SIMILARITY)\n" + "    */\n"
            + "   repeated float floatVector = 2;\n"
            + "   \n"
            + "   optional string text = 3;\n"
            + "   \n"
            + "   /**\n" + "    * @Embedded\n" + "    */\n"
            + "   repeated LangchainMetadataDIMENSION metadata = 4;\n"
            + "}\n";

    @Inject
    private Instance<InfinispanEmbeddingStoreConfig> infinispanEmbeddingStoreConfigHandle;

    @Produces
    public FileDescriptorSource bookProtoDefinition() {
        InfinispanEmbeddingStoreConfig config = infinispanEmbeddingStoreConfigHandle.get();
        String protoContent = PROTO
                .replace("DIMENSION", config.dimension().toString())
                .replace("SIMILARITY", config.similarity());
        return FileDescriptorSource.fromString("langchain_dimension_" + config.dimension().toString() + ".proto",
                protoContent);
    }

    @Produces
    public MessageMarshaller langchainItemMarshaller() {
        return new LangchainItemMarshaller(infinispanEmbeddingStoreConfigHandle.get().dimension());
    }

    @Produces
    public MessageMarshaller langchainMetadataMarshaller() {
        return new LangchainMetadataMarshaller(
                LANGCHAIN_METADATA + infinispanEmbeddingStoreConfigHandle.get().dimension());
    }
}
