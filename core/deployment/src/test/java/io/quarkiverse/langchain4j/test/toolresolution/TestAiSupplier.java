package io.quarkiverse.langchain4j.test.toolresolution;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;

public class TestAiSupplier implements Supplier<ChatModel> {
    @Override
    public ChatModel get() {
        return new TestAiModel();
    }
}
