package org.acme.example;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;

@Path("/oracle/products")
public class OracleNamedStoreResource {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsStore;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public void add(String text) {
        productsStore.add(embeddingModel.embed(text).content(), TextSegment.from(text));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> search(@QueryParam("text") String text) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(text).content())
                .maxResults(10)
                .build();
        return productsStore.search(request).matches().stream()
                .map(match -> match.embedded().text())
                .toList();
    }

    @DELETE
    public void clear() {
        productsStore.removeAll();
    }
}
