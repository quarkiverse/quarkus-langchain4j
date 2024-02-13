package io.quarkiverse.langchain4j.infinispan;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.MessageMarshaller;

import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainItemMarshaller;

@ApplicationScoped
public class SchemaAndMarshallerProducer {

    public static final String LANGCHAIN_ITEM = "LangchainItem";

    private static final String PROTO = "syntax = \"proto2\";\n" + "\n" + "/**\n" + " * @Indexed\n" + " */\n"
            + "message LangchainItemDIMENSION {\n" + "   \n" + "   /**\n" + "    * @Keyword\n" + "    */\n"
            + "   optional string id = 1;\n" + "   \n" + "   /**\n" + "    * @Vector(dimension=DIMENSION, similarity=COSINE)\n"
            + "    */\n" + "   repeated float floatVector = 2;\n" + "   \n" + "   optional string text = 3;\n" + "   \n"
            + "   repeated string metadataKeys = 4;\n" + "   \n" + "   repeated string metadataValues = 5;\n" + "}\n";

    @Inject
    private Instance<InfinispanEmbeddingStoreConfig> infinispanEmbeddingStoreConfigHandle;

    @Produces
    public FileDescriptorSource bookProtoDefinition() {
        Long dimension = infinispanEmbeddingStoreConfigHandle.get().dimension();
        return FileDescriptorSource.fromString("langchain_dimension_" + dimension.toString() + ".proto",
                PROTO.replace("DIMENSION", dimension.toString()));
    }

    @Produces
    public MessageMarshaller langchainItemMarshaller() {
        return new LangchainItemMarshaller(infinispanEmbeddingStoreConfigHandle.get().dimension());
    }
}
