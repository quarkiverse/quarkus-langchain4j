package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestForm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.core.auth.ibmcloud.TokenResponse;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Uni;

@Path("")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public interface IBMCloudAuthRestApi {

    @POST
    @Path("/identity/token")
    TokenResponse token(
            @RestForm(value = "apikey") String apikey,
            @RestForm(value = "grant_type") String grantType);

    @POST
    @Path("/identity/token")
    Uni<TokenResponse> asyncToken(
            @RestForm(value = "apikey") String apikey,
            @RestForm(value = "grant_type") String grantType);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return JsonProvider.MAPPER;
    }

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
