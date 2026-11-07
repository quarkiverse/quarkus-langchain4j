package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.REQUEST_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.TRANSACTION_ID_HEADER;
import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.tool.ToolRequest;
import com.ibm.watsonx.ai.tool.ToolService.Resources;
import com.ibm.watsonx.ai.tool.UtilityTool;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/v1-beta/utility_agent_tools")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface ToolRestApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Resources getAll(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId);

    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    UtilityTool getByName(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            @PathParam("name") String name);

    @POST
    @Path("/run")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> run(
            @HeaderParam(REQUEST_ID_HEADER) String requestId,
            @HeaderParam(TRANSACTION_ID_HEADER) String transactionId,
            ToolRequest request);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
