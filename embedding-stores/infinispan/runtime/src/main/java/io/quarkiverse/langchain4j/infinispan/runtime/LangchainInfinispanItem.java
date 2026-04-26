package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an embedding stored in Infinispan.
 * Holds the embedding vector, optional text content, and a set of typed metadata entries.
 * This is the object that gets serialized to and from Infinispan using Protobuf.
 */
public class LangchainInfinispanItem {

    private String id;

    private float[] floatVector;

    private String text;

    private Set<LangchainMetadata> metadata;

    private Map<String, Object> metadataMap;

    public LangchainInfinispanItem(String id, float[] floatVector, String text, Set<LangchainMetadata> metadata,
            Map<String, Object> metadataMap) {
        this.id = id;
        this.floatVector = floatVector;
        this.text = text;
        this.metadata = metadata;
        this.metadataMap = metadataMap;
    }

    public String getId() {
        return id;
    }

    public float[] getFloatVector() {
        return floatVector;
    }

    public String getText() {
        return text;
    }

    public Set<LangchainMetadata> getMetadata() {
        return metadata;
    }

    public Map<String, Object> getMetadataMap() {
        if (metadataMap == null && metadata != null) {
            metadataMap = new HashMap<>();
            for (LangchainMetadata meta : metadata) {
                metadataMap.put(meta.getName(), meta.getValue());
            }
        }
        return metadataMap;
    }

    @Override
    public String toString() {
        return "LangchainInfinispanItem{" + "id='" + id + '\'' + ", floatVector=" + Arrays.toString(floatVector)
                + ", text='" + text + '\'' + ", metadata=" + metadata + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LangchainInfinispanItem that = (LangchainInfinispanItem) o;
        return Objects.equals(id, that.id) && Arrays.equals(floatVector, that.floatVector) && Objects.equals(text,
                that.text) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, text, metadata);
        result = 31 * result + Arrays.hashCode(floatVector);
        return result;
    }
}
