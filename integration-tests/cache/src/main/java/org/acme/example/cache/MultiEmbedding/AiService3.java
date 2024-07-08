package org.acme.example.cache.MultiEmbedding;

import java.util.List;
import java.util.function.Supplier;

import org.acme.example.cache.MultiEmbedding.AiService3.CustomChatLanguageModel;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "service3", chatLanguageModelSupplier = CustomChatLanguageModel.class)
@CacheResult
public interface AiService3 {
    public String poem(@UserMessage("{text}") String text);

    public static class CustomChatLanguageModel implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new ChatLanguageModel() {
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(AiMessage.from("Hello"));
                }
            };
        }
    }
}
