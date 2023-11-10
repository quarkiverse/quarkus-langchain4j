package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.function.Supplier;

public class MyChatModelSupplier implements Supplier<ChatLanguageModel> {
    @Override
    public ChatLanguageModel get() {
        return OpenAiChatModel.builder()
                .apiKey("...")
                .build();
    }
}
