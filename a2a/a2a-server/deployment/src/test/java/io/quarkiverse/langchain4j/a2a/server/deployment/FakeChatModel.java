package io.quarkiverse.langchain4j.a2a.server.deployment;

import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A model that answers in-process, so that what a test observes is the agent's own behaviour with no provider or
 * network in the way. It is its own supplier, which lets a subclass be named directly in
 * {@code @RegisterAiService(chatLanguageModelSupplier = ...)}.
 */
public abstract class FakeChatModel implements ChatModel, Supplier<ChatModel> {

    @Override
    public ChatModel get() {
        return this;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return ChatResponse.builder().aiMessage(new AiMessage(answer(request))).build();
    }

    protected abstract String answer(ChatRequest request);

    protected static String lastUserText(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        throw new IllegalArgumentException("The request carries no user message");
    }

    protected static int userMessageCount(ChatRequest request) {
        int count = 0;
        for (ChatMessage message : request.messages()) {
            if (message instanceof UserMessage) {
                count++;
            }
        }
        return count;
    }
}
