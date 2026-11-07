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

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

@Path("/oracle")
public class OracleEmbeddingStoreResource {

    private static final String USER_ID = "userId";
    private static final String YEAR = "year";

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @POST
    @Path("/segments")
    @Consumes(MediaType.TEXT_PLAIN)
    public void add(@QueryParam(USER_ID) String userId, @QueryParam(YEAR) int year, String text) {
        Metadata metadata = Metadata.metadata(USER_ID, userId).put(YEAR, year);
        embeddingStore.add(embeddingModel.embed(text).content(), new TextSegment(text, metadata));
    }

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> search(@QueryParam("text") String text, @QueryParam(USER_ID) String userId,
            @QueryParam("minYear") Integer minYear) {
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(text).content())
                .maxResults(10);
        Filter filter = null;
        if (userId != null) {
            filter = MetadataFilterBuilder.metadataKey(USER_ID).isEqualTo(userId);
        }
        if (minYear != null) {
            Filter yearFilter = MetadataFilterBuilder.metadataKey(YEAR).isGreaterThanOrEqualTo(minYear);
            filter = filter == null ? yearFilter : Filter.and(filter, yearFilter);
        }
        request.filter(filter);
        return embeddingStore.search(request.build()).matches().stream()
                .map(match -> match.embedded().text())
                .toList();
    }

    @DELETE
    public void clear() {
        embeddingStore.removeAll();
    }
}
