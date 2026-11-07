package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
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
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageGenerationRequest;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageResponse;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/ml/gateway/v1")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface GatewayImageRestApi {

    @POST
    @Path("images/generations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ModelGatewayImageResponse generate(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @QueryParam("version") String version,
            ModelGatewayImageGenerationRequest request);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
