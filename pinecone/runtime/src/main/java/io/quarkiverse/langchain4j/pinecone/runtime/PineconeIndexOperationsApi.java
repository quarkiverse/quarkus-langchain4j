package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
public interface PineconeIndexOperationsApi {

    @POST
    @Path("/databases")
    void createIndex(CreateIndexRequest request);

    @GET
    @Path("/databases")
    List<String> listIndexes();

}
