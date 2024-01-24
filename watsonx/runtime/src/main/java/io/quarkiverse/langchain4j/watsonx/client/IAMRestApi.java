package io.quarkiverse.langchain4j.watsonx.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.ClientWebApplicationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.IAMError;
import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenRequest;
import io.quarkiverse.langchain4j.watsonx.bean.IdentityTokenResponse;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;

@Path("")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public interface IAMRestApi {

    @POST
    @Path("identity/token")
    IdentityTokenResponse generateBearer(IdentityTokenRequest request);

    @ClientExceptionMapper
    static WebApplicationException toException(jakarta.ws.rs.core.Response response) {

        if (MediaType.TEXT_PLAIN.equals(response.getHeaderString("Content-Type")))
            return new WebApplicationException(response.readEntity(String.class), response.getStatus());

        try {

            IAMError error = response.readEntity(IAMError.class);
            switch (error.errorCode()) {
                case BXNIM0415E:
                    throw new WebApplicationException(
                            "\"quarkus.langchain4j.watsonx.api-key\" is incorrect",
                            response.getStatus());
                default:
                    throw new WebApplicationException(error.errorMessage(), response.getStatus());
            }

        } catch (ClientWebApplicationException | ProcessingException e) {
            return new WebApplicationException(response);
        }
    }

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }
}
