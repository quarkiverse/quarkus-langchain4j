package io.quarkiverse.langchain4j.chroma.runtime;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class QueryRequest {

    private final List<List<Float>> queryEmbeddings;
    private final int nResults;
    private final List<String> include = asList("metadatas", "documents", "distances", "embeddings");

    public QueryRequest(List<Float> queryEmbedding, int nResults) {
        this.queryEmbeddings = singletonList(queryEmbedding);
        this.nResults = nResults;
    }

    public List<List<Float>> getQueryEmbeddings() {
        return queryEmbeddings;
    }

    public int getnResults() {
        return nResults;
    }

    public List<String> getInclude() {
        return include;
    }
}
