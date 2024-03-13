package org.acme.example.mistralai.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.embedding.EmbeddingModel;

@Path("embedding")
public class EmbeddingModelResource {

    private final EmbeddingModel embeddingModel;

    public EmbeddingModelResource(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GET
    public int blocking() {
        return embeddingModel.embed("When was the nobel prize for economics first awarded?").content().dimension();
    }
}
