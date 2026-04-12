package io.quarkiverse.langchain4j.infinispan.runtime;

import java.io.IOException;

import org.infinispan.protostream.MessageMarshaller;

/**
 * Serializes and deserializes {@link LangchainMetadata} to and from Protobuf.
 * Writes the value to the appropriate typed field (String, Long, or Double)
 * and reads it back preserving the original type.
 */
public class LangchainMetadataMarshaller implements MessageMarshaller<LangchainMetadata> {

    private final String typeName;

    public LangchainMetadataMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangchainMetadata readFrom(ProtoStreamReader reader) throws IOException {
        String name = reader.readString("name");
        String valueStr = reader.readString("value");
        Long valueInt = reader.readLong("value_int");
        Double valueFloat = reader.readDouble("value_float");
        Object value = valueStr;

        if (value == null) {
            value = valueInt;
        }
        if (value == null) {
            value = valueFloat;
        }

        return new LangchainMetadata(name, value);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangchainMetadata item) throws IOException {
        writer.writeString("name", item.getName());
        String value = null;
        Long valueInt = null;
        Double valueFloat = null;
        if (item.getValue() instanceof String) {
            value = (String) item.getValue();
        } else if (item.getValue() instanceof Integer) {
            valueInt = ((Integer) item.getValue()).longValue();
        } else if (item.getValue() instanceof Long) {
            valueInt = (Long) item.getValue();
        } else if (item.getValue() instanceof Float) {
            valueFloat = ((Float) item.getValue()).doubleValue();
        } else if (item.getValue() instanceof Double) {
            valueFloat = (Double) item.getValue();
        } else if (item.getValue() != null) {
            value = item.getValue().toString();
        }

        writer.writeString("value", value);
        writer.writeLong("value_int", valueInt);
        writer.writeDouble("value_float", valueFloat);
    }

    @Override
    public Class<? extends LangchainMetadata> getJavaClass() {
        return LangchainMetadata.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
