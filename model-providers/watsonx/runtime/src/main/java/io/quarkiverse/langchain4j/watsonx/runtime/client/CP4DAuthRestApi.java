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

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import com.ibm.watsonx.ai.core.auth.cp4d.TokenRequest;
import com.ibm.watsonx.ai.core.auth.cp4d.TokenResponse;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;

@Path("")
@RegisterProvider(WatsonxJacksonProviders.Reader.class)
@RegisterProvider(WatsonxJacksonProviders.Writer.class)
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
    Uni<JsonObject> legacyTokenAsync(TokenRequest request);

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
    Uni<TokenResponse> iamIdentityTokenAsync(
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
    Uni<JsonObject> iamValidationRequestAsync(@HeaderParam("username") String username,
            @HeaderParam("iam-token") String accessToken);

    @ClientExceptionMapper
    static WatsonxException toException(Response response) {
        return responseToWatsonxException(response);
    }
}
