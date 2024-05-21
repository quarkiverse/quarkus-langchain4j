package io.quarkiverse.langchain4j.infinispan.runtime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.infinispan.protostream.MessageMarshaller;

import io.quarkiverse.langchain4j.infinispan.SchemaAndMarshallerProducer;

/**
 * Marshaller to read and write embeddings to Infinispan
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
        List<String> metadataKeys = reader.readCollection("metadataKeys", new ArrayList<>(), String.class);
        List<String> metadataValues = reader.readCollection("metadataValues", new ArrayList<>(), String.class);
        return new LangchainInfinispanItem(id, floatVector, text, metadataKeys, metadataValues);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangchainInfinispanItem item)
            throws IOException {
        writer.writeString("id", item.getId());
        writer.writeFloats("floatVector", item.getFloatVector());
        writer.writeString("text", item.getText());
        writer.writeCollection("metadataKeys", item.getMetadataKeys(), String.class);
        writer.writeCollection("metadataValues", item.getMetadataValues(), String.class);
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
