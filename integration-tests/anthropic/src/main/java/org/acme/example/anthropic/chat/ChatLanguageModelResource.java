package org.acme.example.anthropic.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.smallrye.mutiny.Multi;

@Path("/chat")
public class ChatLanguageModelResource {
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public ChatLanguageModelResource(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    @GET
    @Path("/blocking")
    public String blocking() {
        return chatModel.chat("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return Multi.createFrom().emitter(emitter -> streamingChatModel.chat(
                "When was the nobel prize for economics first awarded?",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        emitter.emit(token);
                    }

                    @Override
                    public void onError(Throwable error) {
                        emitter.fail(error);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        emitter.complete();
                    }
                }));
    }
}
