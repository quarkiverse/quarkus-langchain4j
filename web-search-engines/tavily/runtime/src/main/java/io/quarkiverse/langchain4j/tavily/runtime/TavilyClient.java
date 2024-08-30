package io.quarkiverse.langchain4j.tavily.runtime;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TavilyClient {

    @POST
    @Path("/search")
    TavilyResponse search(TavilySearchRequest request);
}
