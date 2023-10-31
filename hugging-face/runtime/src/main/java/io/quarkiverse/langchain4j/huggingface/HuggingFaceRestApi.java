package io.quarkiverse.langchain4j.huggingface;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.NotBody;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;

/**
 * This Microprofile REST client is used as the building block of all the API calls to HuggingFace.
 * The implementation is provided by the Reactive REST Client in Quarkus.
 */

@Path("")
@ClientHeaderParam(name = "Authorization", value = "Bearer {token}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface HuggingFaceRestApi {

    @Path("/models/{modelId}")
    @POST
    List<TextGenerationResponse> generate(TextGenerationRequest request, String modelId, @NotBody String token);

    @Path("/pipeline/feature-extraction/{modelId}")
    @POST
    List<float[]> embed(EmbeddingRequest request, String modelId, @NotBody String token);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }
}
