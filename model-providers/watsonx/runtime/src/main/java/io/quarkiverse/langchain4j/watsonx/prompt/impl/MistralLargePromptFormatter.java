package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptToolFormatter;

/**
 * MistralLarge prompt formatter.
 */
public class MistralLargePromptFormatter extends MistralPromptFormatter {

    private static final MistralLargeToolFormatter toolFormatter = new MistralLargeToolFormatter();

    @Override
    public PromptToolFormatter promptToolFormatter() {
        return toolFormatter;
    }

    @Override
    public String toolResult() {
        return "[TOOL_RESULTS] ";
    }

    @Override
    public String toolExecution() {
        return "[TOOL_CALLS] ";
    }

    @Override
    public String endOf(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case AI -> "</s>";
            case SYSTEM -> " [/INST]</s>";
            case USER -> " [/INST]";
            case TOOL_EXECUTION_RESULT -> " [/TOOL_RESULTS] ";
        };
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
        }

        // Prompt without tools.
        return """
                %s\
                %s\
                %s\
                """.formatted(start(), systemMessageFormatter(messages), messagesFormatter(messages));
    }
}
