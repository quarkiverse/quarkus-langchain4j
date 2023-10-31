package io.quarkiverse.langchain4j.chroma.runtime;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class QueryResponse {

    private final List<List<String>> ids;
    private final List<List<List<Float>>> embeddings;
    private final List<List<String>> documents;
    private final List<List<Map<String, String>>> metadatas;
    private final List<List<Double>> distances;

    @JsonCreator
    public QueryResponse(List<List<String>> ids, List<List<List<Float>>> embeddings, List<List<String>> documents,
            List<List<Map<String, String>>> metadatas, List<List<Double>> distances) {
        this.ids = ids;
        this.embeddings = embeddings;
        this.documents = documents;
        this.metadatas = metadatas;
        this.distances = distances;
    }

    public List<List<String>> getIds() {
        return ids;
    }

    public List<List<List<Float>>> getEmbeddings() {
        return embeddings;
    }

    public List<List<String>> getDocuments() {
        return documents;
    }

    public List<List<Map<String, String>>> getMetadatas() {
        return metadatas;
    }

    public List<List<Double>> getDistances() {
        return distances;
    }
}
