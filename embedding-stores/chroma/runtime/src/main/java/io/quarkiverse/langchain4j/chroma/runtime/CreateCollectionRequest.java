package io.quarkiverse.langchain4j.chroma.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CreateCollectionRequest {

    private final String name;
    private final Map<String, String> metadata;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    public CreateCollectionRequest(String name) {
        this.name = name;
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("hnsw:space", "cosine");
        this.metadata = metadata;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
