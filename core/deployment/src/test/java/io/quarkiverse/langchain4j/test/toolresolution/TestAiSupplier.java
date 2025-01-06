package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class TestAiSupplier implements Supplier<ChatLanguageModel> {
    @Override
    public ChatLanguageModel get() {
        return new TestAiModel();
    }
}
