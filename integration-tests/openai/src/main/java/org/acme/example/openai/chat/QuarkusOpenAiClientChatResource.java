package org.acme.example.openai.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.acme.example.openai.MessageUtil;
import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.Delta;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("quarkusClient/chat")
public class QuarkusOpenAiClientChatResource {

    private final QuarkusOpenAiClient quarkusOpenAiClient;

    public QuarkusOpenAiClientChatResource(LangChain4jOpenAiConfig runtimeConfig) {
        String token = runtimeConfig.defaultConfig().apiKey();
        String baseUrl = runtimeConfig.defaultConfig().baseUrl();
        quarkusOpenAiClient = QuarkusOpenAiClient.builder().openAiApiKey(token).baseUrl(baseUrl).build();
    }

    @GET
    @Path("sync")
    public String sync() {
        return quarkusOpenAiClient
                .chatCompletion(
                        MessageUtil.createChatCompletionRequest("Write a short 1 paragraph funny poem about dynamic typing"))
                .execute().content();
    }

    @GET
    @Path("async")
    public Uni<String> async() {
        var request = MessageUtil.createChatCompletionRequest("Write a short 1 paragraph funny poem about Scrum");
        return Uni.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.chatCompletion(request)
                    .onResponse(res -> emitter.complete(res.content()))
                    .onError(emitter::fail)
                    .execute();
        });
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        var request = MessageUtil
                .createChatCompletionRequest("Write a short 1 paragraph funny poem about javascript frameworks");
        return Multi.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.chatCompletion(request)
                    .onPartialResponse(r -> {
                        if (r.choices() != null) {
                            if (r.choices().size() == 1) {
                                ChatCompletionChoice choice = r.choices().get(0);
                                Delta delta = choice.delta();
                                if (delta != null) {
                                    if (delta.content() != null) {
                                        emitter.emit(delta.content());
                                    }
                                } else { // normally this is not needed but mock APIs don't really work with the streaming response
                                    AssistantMessage message = choice.message();
                                    if (message != null) {
                                        emitter.emit(message.content());
                                    }
                                }
                            }
                        }
                    })
                    .onComplete(() -> {
                        emitter.complete();
                    })
                    .onError((t) -> {
                        emitter.fail(t);
                    })
                    .execute();
        });
    }

}
