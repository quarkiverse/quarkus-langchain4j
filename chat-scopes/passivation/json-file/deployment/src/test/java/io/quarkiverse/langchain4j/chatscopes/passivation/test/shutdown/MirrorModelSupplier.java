package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class MirrorModelSupplier implements Supplier<ChatModel> {
    @Override
    public ChatModel get() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(new AiMessage(chatMessageToText(chatRequest.messages().get(0))))
                        .build();
            }
        };
    }
}
