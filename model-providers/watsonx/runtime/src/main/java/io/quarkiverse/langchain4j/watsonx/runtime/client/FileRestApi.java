package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.TRANSACTION_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import java.io.InputStream;

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

import org.jboss.resteasy.reactive.RestForm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.file.FileData;
import com.ibm.watsonx.ai.file.FileDeleteResponse;
import com.ibm.watsonx.ai.file.FileListResponse;

import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Uni;

@Path("/ml/v1/files")
public interface FileRestApi {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    FileData upload(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @RestForm("file") InputStream is, @RestForm("purpose") String purpose);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    FileListResponse list(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @QueryParam("after") String after,
            @QueryParam("limit") Integer limit,
            @QueryParam("order") String order,
            @QueryParam("purpose") String purpose);

    @GET
    @Path("{fileId}/content")
    @Produces(MediaType.TEXT_PLAIN)
    String retrieve(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @PathParam("fileId") String fileId);

    @DELETE
    @Path("{fileId}")
    @Produces(MediaType.APPLICATION_JSON)
    FileDeleteResponse delete(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @PathParam("fileId") String fileId);

    @DELETE
    @Path("{fileId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<FileDeleteResponse> deleteAsync(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @PathParam("fileId") String fileId);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return JsonProvider.MAPPER;
    }

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
