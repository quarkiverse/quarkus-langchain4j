package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptFormatter;

/**
 * MistralLarge prompt formatter.
 */
public class MistralLargePromptFormatter implements PromptFormatter {

    @Override
    public String joiner() {
        return "";
    }

    @Override
    public String start() {
        return "<s>";
    }

    @Override
    public String system() {
        return "[INST] ";
    }

    @Override
    public String user() {
        return "[INST] ";
    }

    @Override
    public String assistant() {
        return "";
    }

    @Override
    public String toolResult() {
        return "</s>[TOOL_RESULTS] ";
    }

    @Override
    public String toolExecution() {
        return "[TOOL_CALLS] ";
    }

    @Override
    public String format(List<ChatMessage> messages, List<ToolSpecification> tools) {
        if (tools != null && tools.size() > 0) {
            return """
                    %s\
                    %s\
                    [AVAILABLE_TOOLS] %s [/AVAILABLE_TOOLS]\
                    %s\
                    """.formatted(start(), systemMessageFormatter(messages), toolsFormatter(tools),
                    messagesFormatter(messages));
        } else {
            return """
                    %s\
                    %s\
                    %s\
                    """.formatted(start(), systemMessageFormatter(messages), messagesFormatter(messages));
        }
    }

    @Override
    public String endOf(ChatMessageType type) {
        return switch (type) {
            case AI -> "</s>";
            case SYSTEM -> " [/INST]</s>";
            case USER -> " [/INST]";
            case TOOL_EXECUTION_RESULT -> " [/TOOL_RESULTS] ";
        };
    }
}
