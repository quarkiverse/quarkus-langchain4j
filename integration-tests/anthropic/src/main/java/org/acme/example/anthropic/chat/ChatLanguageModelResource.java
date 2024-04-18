package org.acme.example.anthropic.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.smallrye.mutiny.Multi;

@Path("/chat")
public class ChatLanguageModelResource {
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;

    public ChatLanguageModelResource(ChatLanguageModel chatModel, StreamingChatLanguageModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    @GET
    @Path("/blocking")
    public String blocking() {
        return chatModel.generate("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return Multi.createFrom().emitter(emitter -> streamingChatModel.generate(
                "When was the nobel prize for economics first awarded?",
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
                    public void onComplete(Response<AiMessage> response) {
                        emitter.complete();
                    }
                }));
    }
}
