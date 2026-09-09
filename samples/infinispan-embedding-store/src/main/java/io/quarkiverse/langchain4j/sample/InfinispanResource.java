package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.segment.TextSegment;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Minimal REST API demonstrating semantic search backed by Infinispan.
 *
 * <pre>
 *   GET /documents/ingest   -> loads the bundled sample documents into Infinispan
 *   GET /documents/search?q=...&max=3 -> returns the most similar documents
 * </pre>
 */
@Path("/documents")
public class InfinispanResource {

    @Inject
    IngestionService ingestionService;

    @GET
    @Path("/ingest")
    @Produces(MediaType.APPLICATION_JSON)
    public String ingest() {
        List<TextSegment> segments = SampleDocuments.all().stream()
                .map(TextSegment::from)
                .toList();
        ingestionService.ingest(segments);
        return "{\"ingested\":" + segments.size() + "}";
    }

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> search(@QueryParam("q") String query, @QueryParam("max") Integer max) {
        int maxResults = max == null ? 3 : max;
        return ingestionService.search(query, maxResults).matches().stream()
                .map(match -> match.embedded().text())
                .toList();
    }
}
