package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.TRANSACTION_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaRequest;
import com.ibm.watsonx.ai.textprocessing.schema.improve.ImproveSchemaResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;

@Path("")
public interface ImproveSchemaRestApi {

    @POST
    @Path("/ml/v1/text/schemas/improve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ImproveSchemaResponse startRequest(
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            ImproveSchemaRequest request);

    @GET
    @Path("/ml/v1/text/schemas/improve/{request_id}")
    @Produces(MediaType.APPLICATION_JSON)
    ImproveSchemaResponse fetchRequestDetails(
            @PathParam("request_id") String requestId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("version") String version);

    @DELETE
    @Path("/ml/v1/text/schemas/improve/{request_id}")
    void deleteRequest(
            @PathParam("request_id") String requestId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("hard_delete") Boolean hardDelete,
            @QueryParam("version") String version);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return JsonProvider.MAPPER;
    }

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
