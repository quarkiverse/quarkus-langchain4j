package org.acme.example.openai.embedding;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

@Path("embedding")
public class EmbeddingModelResource {

    private final EmbeddingModel embeddingModel;

    public EmbeddingModelResource(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    }

    @GET
    @Path("blocking")
    @Produces("text/plain")
    public List<Float> blocking() {
        return embeddingModel.embed("What is the last name of the author of Linux?").content().vectorAsList();
    }
}
