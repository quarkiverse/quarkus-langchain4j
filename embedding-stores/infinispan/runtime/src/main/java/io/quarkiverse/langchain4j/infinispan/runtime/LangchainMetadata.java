package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.Objects;

/**
 * A single metadata entry attached to an embedding.
 * Each entry has a name and a typed value (String, Long, or Double).
 * Stored as an embedded Protobuf message inside {@link LangchainInfinispanItem}.
 */
public class LangchainMetadata {

    private String name;
    private Object value;

    public LangchainMetadata(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LangchainMetadata that = (LangchainMetadata) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "LangchainMetadata{name='" + name + "', value=" + value + '}';
    }
}
