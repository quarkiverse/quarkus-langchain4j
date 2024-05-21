package io.quarkiverse.langchain4j.chroma.runtime;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class DeleteEmbeddingsRequest {

    private List<String> ids;

    public DeleteEmbeddingsRequest(List<String> ids) {
        this.ids = ids;
    }

    public List<String> getIds() {
        return ids;
    }

}
