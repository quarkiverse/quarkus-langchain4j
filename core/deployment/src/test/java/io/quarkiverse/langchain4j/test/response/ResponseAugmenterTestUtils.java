package io.quarkiverse.langchain4j.test.response;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.smallrye.mutiny.Multi;

public class ResponseAugmenterTestUtils {

    @ApplicationScoped
    public static class FakeChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    @ApplicationScoped
    public static class FakeStreamedChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hi!");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    @ApplicationScoped
    public static class UppercaseAugmenter implements AiResponseAugmenter<String> {

        @Override
        public String augment(String response, ResponseAugmenterParams responseAugmenterParams) {
            return response.toUpperCase();
        }
    }

    @ApplicationScoped
    public static class AppenderAugmenter implements AiResponseAugmenter<String> {

        @Override
        public String augment(String response, ResponseAugmenterParams responseAugmenterParams) {
            return response + " WORLD!";
        }

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            return stream.onCompletion()
                    .continueWith(" WONDERFUL!");
        }
    }

    @ApplicationScoped
    public static class StreamedUppercaseAugmenter implements AiResponseAugmenter<String> {

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            return stream.map(String::toUpperCase);
        }
    }

}
