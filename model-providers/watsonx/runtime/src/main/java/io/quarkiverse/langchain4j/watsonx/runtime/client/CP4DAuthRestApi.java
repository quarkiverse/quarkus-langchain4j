package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static io.quarkiverse.langchain4j.watsonx.runtime.client.WatsonxRestClientUtils.responseToWatsonxException;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenRequest;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenResponse;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Uni;

@Path("")
public interface CP4DAuthRestApi {

    @POST
    @Path("/icp4d-api/v1/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    JsonObject legacyToken(TokenRequest request);

    @POST
    @Path("/icp4d-api/v1/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<JsonObject> asyncLegacyToken(TokenRequest request);

    @POST
    @Path("/icp4d-api/v1/authorize")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TokenResponse iamIdentityToken(
            @FormParam("grant_type") String grantType,
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("scope") String scope);

    @POST
    @Path("/icp4d-api/v1/authorize")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TokenResponse> asyncIamIdentityToken(
            @FormParam("grant_type") String grantType,
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("scope") String scope);

    @GET
    @Path("/v1/preauth/validateAuth")
    @Produces(MediaType.APPLICATION_JSON)
    JsonObject iamValidationRequest(@HeaderParam("username") String username, @HeaderParam("iam-token") String accessToken);

    @GET
    @Path("/v1/preauth/validateAuth")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<JsonObject> asyncIamValidationRequest(@HeaderParam("username") String username,
            @HeaderParam("iam-token") String accessToken);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return JsonProvider.MAPPER;
    }

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
