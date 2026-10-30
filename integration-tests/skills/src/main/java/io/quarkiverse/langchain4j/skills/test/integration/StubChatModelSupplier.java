package io.quarkiverse.langchain4j.skills.test.integration;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;

public class StubChatModelSupplier implements Supplier<ChatModel> {

    @Override
    public ChatModel get() {
        return new StubChatModel();
    }
}
