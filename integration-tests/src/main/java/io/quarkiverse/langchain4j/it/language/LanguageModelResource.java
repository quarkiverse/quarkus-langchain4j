package io.quarkiverse.langchain4j.it.language;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import io.smallrye.mutiny.Multi;

@Path("language")
public class LanguageModelResource {

    private final LanguageModel languageModel;
    private final StreamingLanguageModel streamingLanguageModel;

    public LanguageModelResource(LanguageModel languageModel,
            StreamingLanguageModel streamingLanguageModel) {
        this.languageModel = languageModel;
        this.streamingLanguageModel = streamingLanguageModel;
    }

    @GET
    @Path("blocking")
    public String blocking() {
        return languageModel.generate("When was the nobel prize for economics first awarded?").content();
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return Multi.createFrom().emitter(
                emitter -> {
                    streamingLanguageModel.generate(
                            "Write a short 1 paragraph funny poem about Java Applets",
                            new StreamingResponseHandler<>() {
                                @Override
                                public void onNext(String token) {
                                    emitter.emit(token);
                                }

                                @Override
                                public void onError(Throwable error) {
                                    emitter.fail(error);
                                }

                                @Override
                                public void onComplete(Response<String> response) {
                                    emitter.complete();
                                }
                            });
                });
    }
}
