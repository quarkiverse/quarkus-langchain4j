package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.TRANSACTION_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.detection.DetectionContextResponse;
import com.ibm.watsonx.ai.detection.DetectionGeneratedResponse;
import com.ibm.watsonx.ai.detection.DetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionRestClient;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.TextDetectionContentDetectors;
import com.ibm.watsonx.ai.detection.TextDetectionContextDetectors;
import com.ibm.watsonx.ai.detection.TextDetectionGeneratedDetectors;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/ml/v1")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface DetectionRestApi {

    @POST
    @Path("/text/detection")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    DetectionResponse<DetectionTextResponse> detect(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @HeaderParam(DetectionRestClient.GOVERNANCE_INSTANCE_ID_HEADER) String serviceInstanceId,
            @QueryParam("version") String version,
            TextDetectionContentDetectors request);

    @POST
    @Path("/text/detection/context")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    DetectionResponse<DetectionContextResponse> detectContext(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @HeaderParam(DetectionRestClient.GOVERNANCE_INSTANCE_ID_HEADER) String serviceInstanceId,
            @QueryParam("version") String version,
            TextDetectionContextDetectors request);

    @POST
    @Path("/text/detection/generated")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    DetectionResponse<DetectionGeneratedResponse> detectGenerated(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @HeaderParam(DetectionRestClient.GOVERNANCE_INSTANCE_ID_HEADER) String serviceInstanceId,
            @QueryParam("version") String version,
            TextDetectionGeneratedDetectors request);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
