package io.quarkiverse.langchain4j.testing.a2aserver;

import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Answers with the question it was asked, so that a test can tell the message an A2A client sent actually reached
 * the AI Service. Supplying the model here keeps the application free of a model provider, and so of the network.
 */
public class EchoingChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return ChatResponse.builder().aiMessage(new AiMessage("You asked: " + lastUserMessage(request))).build();
    }

    private static String lastUserMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        throw new IllegalStateException("No user message found");
    }
}
