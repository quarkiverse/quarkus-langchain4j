package io.quarkiverse.langchain4j.watsonx.bean;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.base64Image;
import static java.util.Objects.isNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageAssistant;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageSystem;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageUser;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall.TextChatFunctionCall;

public sealed interface TextChatMessage
        permits TextChatMessageAssistant, TextChatMessageSystem, TextChatMessageUser, TextChatMessageTool {
    /**
     * Converts the {@link ChatMessage} into a {@link TextChatMessage}.
     *
     * @param chatMessage the chat message to convert
     * @return the converted {@link TextChatMessage}
     */
    public static TextChatMessage convert(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case AI -> TextChatMessageAssistant.of(AiMessage.class.cast(chatMessage));
            case SYSTEM -> TextChatMessageSystem.of(SystemMessage.class.cast(chatMessage));
            case USER -> TextChatMessageUser.of(UserMessage.class.cast(chatMessage));
            case TOOL_EXECUTION_RESULT -> TextChatMessageTool.of(ToolExecutionResultMessage.class.cast(chatMessage));
        };
    }

    public record TextChatMessageAssistant(String role, String content,
            List<TextChatToolCall> toolCalls) implements TextChatMessage {

        private static final String ROLE = "assistant";

        /**
         * Creates a {@link TextChatMessageAssistant} from a {@link AiMessage}.
         *
         * @param aiMessage the ai message to convert
         * @return the created {@link TextChatMessageAssistant}
         */
        public static TextChatMessageAssistant of(AiMessage aiMessage) {
            if (!aiMessage.hasToolExecutionRequests()) {
                return new TextChatMessageAssistant(ROLE, aiMessage.text(), null);
            }

            // Mapping the tool execution requests
            var toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(TextChatToolCall::of)
                    .toList();

            return new TextChatMessageAssistant(ROLE, aiMessage.text(), toolCalls);
        }

        /**
         * Creates a {@link TextChatMessageAssistant}.
         *
         * @param message the content of the system message to be created.
         * @return the created {@link TextChatMessageAssistant}.
         */
        public static TextChatMessageAssistant of(String message) {
            return new TextChatMessageAssistant(ROLE, message, null);
        }

        /**
         * Creates a {@link TextChatMessageAssistant}.
         *
         * @param toolCalls the tools to execute.
         * @return the created {@link TextChatMessageAssistant}.
         */
        public static TextChatMessageAssistant of(List<TextChatToolCall> toolCalls) {
            return new TextChatMessageAssistant(ROLE, null, toolCalls);
        }
    }

    public record TextChatMessageSystem(String role, String content) implements TextChatMessage {
        private static final String ROLE = "system";

        /**
         * Creates a {@link TextChatMessageSystem} from a {@link SystemMessage}.
         *
         * @param systemMessage the system message to convert
         * @return the created {@link TextChatMessageSystem}.
         */
        public static TextChatMessageSystem of(SystemMessage systemMessage) {
            return new TextChatMessageSystem(ROLE, systemMessage.text());
        }

        /**
         * Creates a {@link TextChatMessageSystem}.
         *
         * @param message the content of the system message to be created.
         * @return the created {@link TextChatMessageSystem}.
         */
        public static TextChatMessageSystem of(String message) {
            return new TextChatMessageSystem(ROLE, message);
        }
    }

    public record TextChatMessageUser(String role, List<Map<String, Object>> content, String name) implements TextChatMessage {

        private static final String ROLE = "user";

        /**
         * Creates a {@link TextChatMessageUser} from a {@link UserMessage}.
         *
         * @param systemMessage the user message to convert
         * @return the created {@link TextChatMessageUser}
         */
        public static TextChatMessageUser of(UserMessage userMessage) {
            var values = new ArrayList<Map<String, Object>>();
            for (Content content : userMessage.contents()) {
                switch (content.type()) {
                    case TEXT -> {
                        var textContent = TextContent.class.cast(content);
                        values.add(Map.of(
                                "type", "text",
                                "text", textContent.text()));
                    }
                    case IMAGE -> {
                        var imageContent = ImageContent.class.cast(content);
                        var mimeType = imageContent.image().mimeType();
                        var base64 = "data:%s;base64,%s".formatted(
                                isNull(mimeType) ? "image" : mimeType,
                                base64Image(imageContent.image()));
                        values.add(Map.of(
                                "type", "image_url",
                                "image_url", Map.of(
                                        "url", base64,
                                        "detail", imageContent.detailLevel().name().toLowerCase())));
                    }
                    case AUDIO, PDF, TEXT_FILE, VIDEO ->
                        throw new UnsupportedOperationException("Unimplemented case: " + content.type());
                }
            }
            return new TextChatMessageUser(ROLE, values, userMessage.name());
        }

        /**
         * Creates a {@link TextChatMessageUser}.
         *
         * @param message the content of the system message to be created.
         * @return the created {@link TextChatMessageUser}.
         */
        public static TextChatMessageUser of(String message) {
            return of(UserMessage.from(message));
        }
    }

    public record TextChatMessageTool(String role, String content, String toolCallId) implements TextChatMessage {

        private static final String ROLE = "tool";

        /**
         * Creates a {@link TextChatMessageTool} from a {@link ToolExecutionResultMessage}.
         *
         * @param toolExecutionResultMessage the tool execution result message to convert
         * @return the created {@link TextChatMessageTool}
         */
        public static TextChatMessageTool of(ToolExecutionResultMessage toolExecutionResultMessage) {
            return new TextChatMessageTool(ROLE, toolExecutionResultMessage.text(), toolExecutionResultMessage.id());
        }

        /**
         * Creates a {@link TextChatMessageTool}.
         *
         * @param content the content of the message tool.
         * @param toolCallId the unique identifier of the message tool.
         * @return the created {@link TextChatMessageTool}.
         */
        public static TextChatMessageTool of(String content, String toolCallId) {
            return new TextChatMessageTool(ROLE, content, toolCallId);
        }
    }

    public record TextChatToolCall(Integer index, String id, String type, TextChatFunctionCall function) {
        public record TextChatFunctionCall(String name, String arguments) {
        }

        /**
         * Creates a {@link TextChatToolCall} from a {@link ToolExecutionRequest}.
         *
         * @param toolExecutionRequest the tool execution request to convert
         * @return the created {@link TextChatToolCall}
         */
        public static TextChatToolCall of(ToolExecutionRequest toolExecutionRequest) {
            return new TextChatToolCall(null, toolExecutionRequest.id(), "function",
                    new TextChatFunctionCall(toolExecutionRequest.name(), toolExecutionRequest.arguments()));
        }

        /**
         * Converts a {@link TextChatToolCall} into a {@link ToolExecutionRequest}.
         *
         * @return the converted {@link ToolExecutionRequest}
         */
        public ToolExecutionRequest convert() {
            return ToolExecutionRequest.builder()
                    .id(id)
                    .name(function.name)
                    .arguments(function.arguments)
                    .build();
        }
    }

    public record TextChatParameterTool(String type, TextChatParameterFunction function) {
        public record TextChatParameterFunction(String name, String description, Map<String, Object> parameters) {
        }

        /**
         * Creates a {@link TextChatParameterTool} from a {@link ToolSpecification}.
         *
         * @param toolSpecification the tool specification to convert
         * @return the created {@link TextChatParameterTool}
         */
        public static TextChatParameterTool of(ToolSpecification toolSpecification) {
            var toolParams = JsonSchemaElementHelper.toMap(toolSpecification.parameters());
            var parameters = new TextChatParameterFunction(toolSpecification.name(), toolSpecification.description(),
                    toolParams);
            return new TextChatParameterTool("function", parameters);
        }
    }

    /**
     * The {@code StreamingToolFetcher} class is responsible for fetching a list of tools from a streaming API.
     */
    public class StreamingToolFetcher {

        private int index;
        private StringBuilder arguments;
        private String id, type, name;

        public StreamingToolFetcher(int index) {
            this.index = index;
            arguments = new StringBuilder();
        }

        public void setId(String id) {
            if (id != null)
                this.id = id;
        }

        public void setType(String type) {
            if (type != null)
                this.type = type;
        }

        public void setName(String name) {
            if (name != null && !name.isBlank())
                this.name = name;
        }

        public void appendArguments(String arguments) {
            if (arguments != null)
                this.arguments.append(arguments);
        }

        public TextChatToolCall build() {
            return new TextChatToolCall(index, id, type, new TextChatFunctionCall(name, arguments.toString()));
        }
    }
}
