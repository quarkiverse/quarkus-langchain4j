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

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionRequest;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface TextExtractionRestApi {

    @DELETE
    @Path("/ml/v1/text/extractions/{extraction_id}")
    void deleteExtraction(
            @PathParam("extraction_id") String extractionId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("hard_delete") Boolean hardDelete,
            @QueryParam("version") String version);

    @GET
    @Path("/ml/v1/text/extractions/{extraction_id}")
    @Produces(MediaType.APPLICATION_JSON)
    TextExtractionResponse fetchExtractionDetails(
            @PathParam("extraction_id") String extractionId,
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("project_id") String projectId,
            @QueryParam("space_id") String spaceId,
            @QueryParam("version") String version);

    @POST
    @Path("/ml/v1/text/extractions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TextExtractionResponse startExtraction(
            @HeaderParam(REQUEST_ID_HEADER) String watsonxAISDKRequestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            TextExtractionRequest textExtractionRequest);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
