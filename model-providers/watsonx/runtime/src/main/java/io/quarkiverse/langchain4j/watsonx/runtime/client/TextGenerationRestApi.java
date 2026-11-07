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
import org.jboss.resteasy.reactive.RestStreamElementType;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.textgeneration.TextGenerationResponse;
import com.ibm.watsonx.ai.textgeneration.TextRequest;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Multi;

@Path("/ml/v1")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface TextGenerationRestApi {

    @POST
    @Path("/text/generation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TextGenerationResponse generate(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            TextRequest textRequest);

    @POST
    @Path("text/generation_stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<String> generateStreaming(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @QueryParam("version") String version,
            TextRequest request);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
