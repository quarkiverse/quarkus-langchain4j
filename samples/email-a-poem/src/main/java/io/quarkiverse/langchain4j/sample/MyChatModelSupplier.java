package io.quarkiverse.langchain4j.sample;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class MyChatModelSupplier implements Supplier<ChatLanguageModel> {
    @Override
    public ChatLanguageModel get() {
        return OpenAiChatModel.builder()
                .apiKey("...")
                .build();
    }
}
