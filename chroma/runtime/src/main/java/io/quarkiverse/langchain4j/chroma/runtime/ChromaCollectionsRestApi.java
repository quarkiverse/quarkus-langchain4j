package io.quarkiverse.langchain4j.chroma.runtime;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;

@Path("/api/v1/collections")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ChromaCollectionsRestApi {

    @Path("/{collectionName}")
    @GET
    Collection collection(String collectionName);

    @POST
    Collection createCollection(CreateCollectionRequest createCollectionRequest);

    @Path("/{collectionId}/add")
    @POST
    Boolean addEmbeddings(String collectionId, AddEmbeddingsRequest embedding);

    @Path("{collectionId}/query")
    @POST
    QueryResponse queryCollection(String collectionId, QueryRequest queryRequest);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }
}
