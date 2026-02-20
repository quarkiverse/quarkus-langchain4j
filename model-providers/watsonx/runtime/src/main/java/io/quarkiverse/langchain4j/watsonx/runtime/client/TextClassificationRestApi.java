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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.WatsonxJacksonModule;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationRequest;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Uni;

@Path("")
public interface TextClassificationRestApi {

    @POST
    @Path("/ml/v1/text/classifications")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TextClassificationResponse startClassification(
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            TextClassificationRequest request);

    @GET
    @Path("/ml/v1/text/classifications/{classification_id}")
    @Produces(MediaType.APPLICATION_JSON)
    TextClassificationResponse fetchClassificationDetails(
            @PathParam("classification_id") String classificationId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("version") String version);

    @DELETE
    @Path("/ml/v1/text/classifications/{classification_id}")
    void deleteClassification(
            @PathParam("classification_id") String classificationId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("hard_delete") Boolean hardDelete,
            @QueryParam("version") String version);

    @DELETE
    @Path("{bucket_name}/{file_name}")
    void deleteFile(
            @PathParam("bucket_name") String bucketName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId);

    @DELETE
    @Path("{bucket_name}/{file_name}")
    Uni<Void> asyncDeleteFile(
            @PathParam("bucket_name") String bucketName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId);

    @PUT
    @Path("{bucket_name}/{file_name}")
    void upload(
            @PathParam("bucket_name") String buckedName,
            @PathParam("file_name") String fileName,
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            InputStream is);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER.copy().registerModule(new WatsonxJacksonModule());
    }

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
