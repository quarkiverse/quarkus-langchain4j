package io.quarkiverse.langchain4j.cohere.runtime.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CohereApi {

    @Path("/rerank")
    @POST
    RerankResponse rerank(RerankRequest rerankRequest);

}
