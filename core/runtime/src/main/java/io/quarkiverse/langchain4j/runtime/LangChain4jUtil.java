package io.quarkiverse.langchain4j.runtime;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public class LangChain4jUtil {

    private LangChain4jUtil() {
    }

    public static String chatMessageToText(ChatMessage chatMessage) {
        if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.hasSingleText() ? userMessage.singleText() : null;
        }
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        }
        return null;
    }
}
