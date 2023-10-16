package io.quarkiverse.langchain4j.it.language;

import static io.quarkiverse.langchain4j.it.MessageUtil.createCompletionRequest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.ai4j.openai4j.completion.CompletionChoice;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.OpenAi;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("quarkusClient/language")
public class QuarkusOpenAiClientLanguageResource {

    private final QuarkusOpenAiClient quarkusOpenAiClient;

    public QuarkusOpenAiClientLanguageResource(LangChain4jRuntimeConfig runtimeConfig) {
        OpenAi openAi = runtimeConfig.openAi();
        String token = openAi.apiKey().get();
        String baseUrl = openAi.baseUrl();
        quarkusOpenAiClient = QuarkusOpenAiClient.builder().openAiApiKey(token).baseUrl(baseUrl).build();
    }

    @GET
    @Path("sync")
    public String sync() {
        return quarkusOpenAiClient
                .completion(createCompletionRequest("Write a short 1 paragraph funny poem about dynamic typing"))
                .execute().text();
    }

    @GET
    @Path("async")
    public Uni<String> async() {
        var request = createCompletionRequest("Write a short 1 paragraph funny poem about Scrum");
        return Uni.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.completion(request)
                    .onResponse(res -> emitter.complete(res.text()))
                    .onError(emitter::fail)
                    .execute();
        });
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        var request = createCompletionRequest("Write a short 1 paragraph funny poem about javascript frameworks");
        return Multi.createFrom().emitter(emitter -> {
            quarkusOpenAiClient.completion(request)
                    .onPartialResponse(r -> {
                        if (r.choices() != null) {
                            if (r.choices().size() == 1) {
                                CompletionChoice choice = r.choices().get(0);
                                String text = choice.text();
                                if (text != null) {
                                    emitter.emit(text);
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
