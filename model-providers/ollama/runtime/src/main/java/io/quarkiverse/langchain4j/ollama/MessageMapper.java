package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ContentType.TEXT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

// TODO: this could use a lot of refactoring
final class MessageMapper {

    static {
        new TypeReference<>() {
        };
    }

    private MessageMapper() {
    }

    private final static Predicate<UserMessage> hasImages = userMessage -> userMessage.contents().stream()
            .anyMatch(content -> IMAGE.equals(content.type()));

    static List<Message> toOllamaMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (ChatMessage chatMessage : messages) {
            if ((chatMessage instanceof UserMessage userMessage) && hasImages.test(userMessage)) {
                result.add(messagesWithImageSupport(userMessage));
            } else {
                result.add(otherMessages(chatMessage));
            }
        }
        return result;
    }

    private static Message messagesWithImageSupport(UserMessage userMessage) {
        Map<ContentType, List<Content>> groupedContents = userMessage.contents().stream()
                .collect(Collectors.groupingBy(Content::type));

        if (groupedContents.get(TEXT).size() != 1) {
            throw new RuntimeException("Expecting single text content, but got: " + userMessage.contents());
        }

        String text = ((TextContent) groupedContents.get(TEXT).get(0)).text();

        List<ImageContent> imageContents = groupedContents.get(IMAGE).stream()
                .map(content -> (ImageContent) content)
                .collect(Collectors.toList());

        return Message.builder()
                .role(toOllamaRole(userMessage.type()))
                .content(text)
                .images(ImageUtils.base64EncodeImageList(imageContents))
                .build();
    }

    private static Message otherMessages(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
            if (!aiMessage.hasToolExecutionRequests()) {
                return Message.builder()
                        .role(toOllamaRole(ChatMessageType.AI))
                        .content(aiMessage.text())
                        .build();
            }

            try {
                List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
                List<ToolCall> toolCalls = new ArrayList<>(toolExecutionRequests.size());
                for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
                    String argumentsStr = toolExecutionRequest.arguments();
                    String name = toolExecutionRequest.name();
                    // TODO: we need to update LangChain4j to make ToolExecutionRequest use a map instead of a String
                    Map<String, Object> arguments = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(argumentsStr,
                            Map.class);
                    toolCalls.add(ToolCall.fromFunctionCall(name, arguments));
                }

                return Message.builder()
                        .role(toOllamaRole(ChatMessageType.AI))
                        .toolCalls(toolCalls)
                        .build();
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to perform conversion of tool response", e);
            }
        }

        if (message instanceof ToolExecutionResultMessage) {
            return Message.builder()
                    .role(toOllamaRole(TOOL_EXECUTION_RESULT))
                    .content(message.text())
                    .build();
        }

        return Message.builder()
                .role(toOllamaRole(message.type()))
                .content(message.text())
                .build();
    }

    private static Role toOllamaRole(ChatMessageType chatMessageType) {
        return switch (chatMessageType) {
            case SYSTEM -> Role.SYSTEM;
            case USER -> Role.USER;
            case AI -> Role.ASSISTANT;
            case TOOL_EXECUTION_RESULT -> Role.TOOL;
        };
    }

    static List<Tool> toTools(Collection<ToolSpecification> toolSpecifications) {
        if (toolSpecifications.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tool> result = new ArrayList<>(toolSpecifications.size());
        for (ToolSpecification toolSpecification : toolSpecifications) {
            result.add(toTool(toolSpecification));
        }
        return result;
    }

    private static Tool toTool(ToolSpecification toolSpecification) {
        Tool.Function.Parameters functionParameters;
        if (toolSpecification.toolParameters() != null) {
            functionParameters = toFunctionParameters(toolSpecification.toolParameters());
        } else {
            functionParameters = toFunctionParameters(toolSpecification.parameters());
        }
        return new Tool(Tool.Type.FUNCTION, new Tool.Function(toolSpecification.name(), toolSpecification.description(),
                functionParameters));
    }

    private static Tool.Function.Parameters toFunctionParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return Tool.Function.Parameters.empty();
        }
        return Tool.Function.Parameters.objectType(toolParameters.properties(), toolParameters.required());
    }

    private static Tool.Function.Parameters toFunctionParameters(JsonObjectSchema parameters) {
        if (parameters == null) {
            return Tool.Function.Parameters.empty();
        }
        return Tool.Function.Parameters.objectType(JsonSchemaElementHelper.toMap(parameters.properties()),
                parameters.required());
    }

}
