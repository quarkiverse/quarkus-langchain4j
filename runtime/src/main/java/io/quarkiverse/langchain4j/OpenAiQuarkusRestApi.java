package io.quarkiverse.langchain4j;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkus.rest.client.reactive.NotBody;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ClientHeaderParam(name = "Authorization", value = "Bearer {token}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface OpenAiQuarkusRestApi {

    @Path("chat/completions")
    @POST
    @NoStream
    Uni<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request, @NotBody String token);

    @Path("chat/completions")
    @POST
    @NoStream
    ChatCompletionResponse blockingCreateChatCompletion(ChatCompletionRequest request, @NotBody String token);

    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Stream
    Multi<ChatCompletionResponse> streamingCreateChatCompletion(ChatCompletionRequest request, @NotBody String token);

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return defaultObjectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
