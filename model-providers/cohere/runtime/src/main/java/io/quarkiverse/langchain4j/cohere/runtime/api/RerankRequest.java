package io.quarkiverse.langchain4j.cohere.runtime.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RerankRequest {

    private final String model;
    private final String query;
    private final List<String> documents;

    public RerankRequest(String model, String query, List<String> documents) {
        this.model = model;
        this.query = query;
        this.documents = documents;
    }

    public String getModel() {
        return model;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getDocuments() {
        return documents;
    }
}
