package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.TRANSACTION_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.batch.BatchCreateRequest;
import com.ibm.watsonx.ai.batch.BatchData;
import com.ibm.watsonx.ai.batch.BatchListResponse;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/ml/v1/batches")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface BatchRestApi {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    BatchData submit(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            BatchCreateRequest request);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    BatchListResponse list(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @QueryParam("limit") Integer limit);

    @GET
    @Path("{batchId}")
    @Produces(MediaType.APPLICATION_JSON)
    BatchData retrieve(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @PathParam("batchId") String batchId);

    @POST
    @Path("{batchId}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    BatchData cancel(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            @HeaderParam("X-IBM-Project-ID") String projectId,
            @HeaderParam("X-IBM-Space-ID") String spaceId,
            @PathParam("batchId") String batchId);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
