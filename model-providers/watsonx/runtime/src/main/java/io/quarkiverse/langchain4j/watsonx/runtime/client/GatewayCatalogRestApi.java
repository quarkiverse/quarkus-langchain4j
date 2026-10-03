package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayListModelsResponse;
import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayModel;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/ml/gateway/v1/models")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface GatewayCatalogRestApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    ModelGatewayListModelsResponse listModels(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @QueryParam("version") String version);

    @GET
    @Path("{model_id}")
    @Produces(MediaType.APPLICATION_JSON)
    ModelGatewayModel getModel(
            @PathParam("model_id") String modelId,
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @QueryParam("version") String version);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
