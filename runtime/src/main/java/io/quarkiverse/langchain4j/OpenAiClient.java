package io.quarkiverse.langchain4j;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Uni;

@RegisterRestClient(baseUri = "https://api.openai.com")
@ClientHeaderParam(name = "Authorization", value = "Bearer ${openai.key}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface OpenAiClient {

    @Path("/v1/chat/completions")
    @POST
    Uni<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return defaultObjectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
