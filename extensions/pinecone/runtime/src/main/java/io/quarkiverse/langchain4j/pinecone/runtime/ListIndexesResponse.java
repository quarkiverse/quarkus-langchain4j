package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ListIndexesResponse {

    private final List<DescribeIndexResponse> indexes;

    @JsonCreator
    public ListIndexesResponse(List<DescribeIndexResponse> indexes) {
        this.indexes = indexes;
    }

    public List<DescribeIndexResponse> getIndexes() {
        return indexes;
    }
}
