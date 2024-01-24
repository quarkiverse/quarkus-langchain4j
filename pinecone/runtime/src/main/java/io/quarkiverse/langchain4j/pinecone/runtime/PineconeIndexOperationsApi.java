package io.quarkiverse.langchain4j.pinecone.runtime;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
public interface PineconeIndexOperationsApi {

    @POST
    @Path("/indexes")
    void createIndex(CreateIndexRequest request);

    @GET
    @Path("/indexes")
    ListIndexesResponse listIndexes();

    @GET
    @Path("/indexes/{indexName}")
    DescribeIndexResponse describeIndex(String indexName);

    @DELETE
    @Path("/indexes/{indexName}")
    void deleteIndex(@PathParam("indexName") String indexName);

}
