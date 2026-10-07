package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

@RegisterRestClient(configKey = "contracts")
@Path("/groups/{group}/artifacts/{artifact}")
@Produces(MediaType.APPLICATION_JSON)
public interface ContractClient {
    @GET
    @Path("/versions/branch=latest")
    JsonNode metadata(@PathParam("group") String group, @PathParam("artifact") String artifact);

}
