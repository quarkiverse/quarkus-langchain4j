package org.acme.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.ClientProxy;

@Path("embedding")
public class Resource {

    private final EmbeddingModel embeddingModel;

    public Resource(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GET
    public String get() {
        return ClientProxy.unwrap(embeddingModel).getClass().getSimpleName();
    }
}
