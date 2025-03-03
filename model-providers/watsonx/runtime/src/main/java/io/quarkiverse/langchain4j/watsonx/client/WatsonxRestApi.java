package io.quarkiverse.langchain4j.watsonx.client;

import java.util.StringJoiner;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingResponse;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringRequest;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextExtractionResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextStreamingChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Multi;

/**
 * This Microprofile REST client is used as the building block of all the API calls to watsonx. The implementation is provided
 * by
 * the Reactive REST Client in Quarkus.
 */
@Path("/ml/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface WatsonxRestApi {

    @POST
    @Path("text/generation")
    TextGenerationResponse generation(TextGenerationRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("text/generation_stream")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<TextGenerationResponse> generationStreaming(TextGenerationRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("text/chat")
    TextChatResponse chat(TextChatRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("text/chat_stream")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<TextStreamingChatResponse> streamingChat(TextChatRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("text/rerank")
    ScoringResponse rerank(ScoringRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("text/tokenization")
    TokenizationResponse tokenization(TokenizationRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("/text/embeddings")
    EmbeddingResponse embeddings(EmbeddingRequest request,
            @QueryParam("version") String version);

    @POST
    @Path("/text/extractions")
    TextExtractionResponse startTextExtractionJob(TextExtractionRequest request,
            @QueryParam("version") String version);

    @GET
    @Path("text/extractions/{id}")
    TextExtractionResponse getTextExtractionDetails(@PathParam("id") String id,
            @QueryParam("space_id") String spaceId,
            @QueryParam("project_id") String projectId,
            @QueryParam("version") String version);

    @ClientExceptionMapper
    static WatsonxException toException(jakarta.ws.rs.core.Response response) {
        MediaType mediaType = response.getMediaType();
        if ((mediaType != null) && mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            try {

                WatsonxError ex = response.readEntity(WatsonxError.class);
                StringJoiner joiner = new StringJoiner("\n");

                if (ex.errors() != null && ex.errors().size() > 0) {
                    for (WatsonxError.Error error : ex.errors())
                        joiner.add("%s: %s".formatted(error.code(), error.message()));
                }

                return new WatsonxException(joiner.toString(), response.getStatus(), ex);
            } catch (Exception e) {
                return new WatsonxException(response.readEntity(String.class), response.getStatus());
            }
        }

        return new WatsonxException(response.readEntity(String.class), response.getStatus());
    }

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }
}
