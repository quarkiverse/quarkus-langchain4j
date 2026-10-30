package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestForm;

import com.ibm.watsonx.ai.core.auth.ibmcloud.TokenResponse;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;

@Path("")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
public interface IBMCloudAuthRestApi {

    @POST
    @Path("/identity/token")
    TokenResponse token(
            @RestForm(value = "apikey") String apikey,
            @RestForm(value = "grant_type") String grantType);

    @POST
    @Path("/identity/token")
    Uni<TokenResponse> tokenAsync(
            @RestForm(value = "apikey") String apikey,
            @RestForm(value = "grant_type") String grantType);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
