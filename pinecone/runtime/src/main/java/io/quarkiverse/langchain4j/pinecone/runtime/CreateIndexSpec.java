package io.quarkiverse.langchain4j.pinecone.runtime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CreateIndexSpec {

    private final CreateIndexPodSpec pod;

    public CreateIndexSpec(CreateIndexPodSpec pod) {
        this.pod = pod;
    }

    public CreateIndexPodSpec getPod() {
        return pod;
    }
}
