package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import java.io.InputStream;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;

@Path("")
public interface CosStorageRestApi {

    @PUT
    @Path("{bucket_name}/{file_name}")
    void upload(
            @PathParam("bucket_name") String bucketName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            InputStream is);

    @GET
    @Path("{bucket_name}/{file_name}")
    String readFile(
            @PathParam("bucket_name") String bucketName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId);

    @DELETE
    @Path("{bucket_name}/{file_name}")
    Uni<Void> deleteFileAsync(
            @PathParam("bucket_name") String bucketName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
