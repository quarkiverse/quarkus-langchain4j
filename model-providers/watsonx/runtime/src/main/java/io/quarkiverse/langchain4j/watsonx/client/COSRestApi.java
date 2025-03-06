package io.quarkiverse.langchain4j.watsonx.client;

import java.io.InputStream;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.langchain4j.watsonx.bean.CosError;
import io.quarkiverse.langchain4j.watsonx.exception.COSException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;

public interface COSRestApi {

    @PUT
    @Path("{bucketName}/{fileName}")
    public Response createFile(@PathParam("bucketName") String bucketName,
            @PathParam("fileName") String fileName, InputStream is);

    @GET
    @Path("{bucketName}/{fileName}")
    public String getFileContent(@PathParam("bucketName") String bucketName,
            @PathParam("fileName") String fileName);

    @DELETE
    @Path("{bucketName}/{fileName}")
    Uni<Response> deleteFile(@PathParam("bucketName") String bucketName,
            @PathParam("fileName") String fileName);

    @HEAD
    @Path("{bucketName}/{fileName}")
    public void getFileMetadata(@PathParam("bucketName") String bucketName,
            @PathParam("fileName") String fileName);

    @ClientExceptionMapper
    static COSException toException(jakarta.ws.rs.core.Response response) {
        if (MediaType.APPLICATION_XML.equals(response.getHeaderString("Content-Type"))) {
            CosError error = response.readEntity(CosError.class);
            return new COSException(error.getMessage(), error, response.getStatus());
        }
        return new COSException(response.readEntity(String.class), response.getStatus());
    }
}
