package io.quarkiverse.langchain4j.infinispan.runtime;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.infinispan.protostream.MessageMarshaller;

import io.quarkiverse.langchain4j.infinispan.SchemaAndMarshallerProducer;

/**
 * Serializes and deserializes {@link LangchainInfinispanItem} to and from Protobuf
 * for storage in Infinispan. Handles the embedding vector, text, and metadata collection.
 */
public class LangchainItemMarshaller implements MessageMarshaller<LangchainInfinispanItem> {
    private final String typeName;

    public LangchainItemMarshaller(Long dimension) {
        this.typeName = SchemaAndMarshallerProducer.LANGCHAIN_ITEM + dimension.toString();
    }

    @Override
    public LangchainInfinispanItem readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString("id");
        float[] floatVector = reader.readFloats("floatVector");
        String text = reader.readString("text");
        Set<LangchainMetadata> metadata = reader.readCollection("metadata", new HashSet<>(), LangchainMetadata.class);

        Map<String, Object> metadataMap = new HashMap<>();
        if (metadata != null) {
            for (LangchainMetadata meta : metadata) {
                metadataMap.put(meta.getName(), meta.getValue());
            }
        }
        return new LangchainInfinispanItem(id, floatVector, text, metadata, metadataMap);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangchainInfinispanItem item)
            throws IOException {
        writer.writeString("id", item.getId());
        writer.writeFloats("floatVector", item.getFloatVector());
        writer.writeString("text", item.getText());
        writer.writeCollection("metadata", item.getMetadata(), LangchainMetadata.class);
    }

    @Override
    public Class<? extends LangchainInfinispanItem> getJavaClass() {
        return LangchainInfinispanItem.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
