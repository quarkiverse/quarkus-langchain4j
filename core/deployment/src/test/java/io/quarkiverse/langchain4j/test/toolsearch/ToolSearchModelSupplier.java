package io.quarkiverse.langchain4j.test.toolsearch;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;

public class ToolSearchModelSupplier implements Supplier<ChatModel> {
    @Override
    public ChatModel get() {
        return new ToolSearchModel();
    }
}
