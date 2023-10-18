package org.acme.example.embedding;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.model.embedding.EmbeddingModel;

@Path("embedding")
public class EmbeddingModelResource {

    private final EmbeddingModel embeddingModel;

    public EmbeddingModelResource(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GET
    @Path("blocking")
    @Produces("text/plain")
    public List<Float> blocking() {
        return embeddingModel.embed("This is some text").content().vectorAsList();
    }
}
