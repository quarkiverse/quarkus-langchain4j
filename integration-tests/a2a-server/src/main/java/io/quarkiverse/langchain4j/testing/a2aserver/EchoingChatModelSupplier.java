package io.quarkiverse.langchain4j.testing.a2aserver;

import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;

public class EchoingChatModelSupplier implements Supplier<ChatModel> {

    @Override
    public ChatModel get() {
        return new EchoingChatModel();
    }
}
