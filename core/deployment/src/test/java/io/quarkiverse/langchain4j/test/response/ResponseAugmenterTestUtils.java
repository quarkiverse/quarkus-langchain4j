package io.quarkiverse.langchain4j.test.response;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.smallrye.mutiny.Multi;

public class ResponseAugmenterTestUtils {

    public static class FakeChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new FakeChatModel();
        }
    }

    public static class FakeChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("Hi!"));
        }
    }

    public static class FakeMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }

    public static class FakeStreamedChatModelSupplier implements Supplier<StreamingChatLanguageModel> {

        @Override
        public StreamingChatLanguageModel get() {
            return new FakeStreamedChatModel();
        }
    }

    public static class FakeStreamedChatModel implements StreamingChatLanguageModel {

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            handler.onNext("Hi!");
            handler.onNext(" ");
            handler.onNext("World!");
            handler.onComplete(Response.from(AiMessage.from("")));
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
