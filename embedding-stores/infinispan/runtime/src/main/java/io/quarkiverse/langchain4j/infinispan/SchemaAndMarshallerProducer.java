package io.quarkiverse.langchain4j.infinispan;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.protostream.FileDescriptorSource;

import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanEmbeddingStoreConfig;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanStoreRuntimeConfig;

/**
 * Produces the Protobuf schema needed by the Infinispan client
 * to store and retrieve embeddings. The schema defines the structure of the
 * embedding item and its metadata, including the vector field with configurable
 * dimension and similarity metric.
 * <p>
 * One proto file is generated per unique (dimension, similarity) pair discovered
 * across the default store and all named stores. Marshallers are produced as
 * CDI synthetic beans by the deployment processor (one per store).
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
    Instance<InfinispanEmbeddingStoreConfig> infinispanEmbeddingStoreConfigHandle;

    /**
     * Produces a single {@link FileDescriptorSource} that contains one Protobuf schema
     * per unique (dimension, similarity) pair across the default store and all named stores.
     * This ensures that every configured embedding store has its vector type registered
     * in the Infinispan serialization context regardless of the number of stores or
     * how many distinct dimensions are in use.
     */
    @Produces
    public FileDescriptorSource bookProtoDefinition() {
        InfinispanEmbeddingStoreConfig config = infinispanEmbeddingStoreConfigHandle.get();

        Map<Long, String> dimensionToSimilarity = new LinkedHashMap<>();
        InfinispanStoreRuntimeConfig def = config.defaultConfig();
        dimensionToSimilarity.put(def.dimension(), def.similarity());

        for (InfinispanStoreRuntimeConfig named : config.namedConfig().values()) {
            dimensionToSimilarity.putIfAbsent(named.dimension(), named.similarity());
        }

        FileDescriptorSource source = new FileDescriptorSource();
        for (Map.Entry<Long, String> entry : dimensionToSimilarity.entrySet()) {
            Long dimension = entry.getKey();
            String similarity = entry.getValue();
            String protoContent = PROTO
                    .replace("DIMENSION", dimension.toString())
                    .replace("SIMILARITY", similarity);
            source.addProtoFile("langchain_dimension_" + dimension + ".proto", protoContent);
        }
        return source;
    }
}
