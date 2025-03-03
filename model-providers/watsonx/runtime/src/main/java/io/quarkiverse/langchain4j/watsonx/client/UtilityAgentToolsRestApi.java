package io.quarkiverse.langchain4j.watsonx.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.Experimental;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsResponse;
import io.quarkiverse.langchain4j.watsonx.exception.BuiltinServiceException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;

@Experimental
@Path("/v1-beta/utility_agent_tools")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UtilityAgentToolsRestApi {

    @POST
    @Path("run")
    public UtilityAgentToolsResponse run(UtilityAgentToolsRequest request);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }

    @ClientExceptionMapper
    static BuiltinServiceException toException(jakarta.ws.rs.core.Response response) {
        MediaType mediaType = response.getMediaType();
        if ((mediaType != null) && mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {

            // In the beta version, the web server does not specify all possible exceptions.
            // Review and update the exception handling in the release version to ensure all cases are covered.

            if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
                ObjectNode jsonObject = response.readEntity(ObjectNode.class);

                try {

                    String description = jsonObject.get("description").asText();
                    return new BuiltinServiceException(description, response.getStatus());

                } catch (Exception e) {
                    return new BuiltinServiceException(response.readEntity(String.class), response.getStatus());
                }
            }
        }
        return new BuiltinServiceException(response.readEntity(String.class), response.getStatus());
    }
}
