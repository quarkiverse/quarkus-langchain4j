package org.acme.example.openai.chat.ollama;

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
    @Produces("text/plain")
    public List<Float> get() {
        return embeddingModel.embed("This is some text").content().vectorAsList();
    }
}
