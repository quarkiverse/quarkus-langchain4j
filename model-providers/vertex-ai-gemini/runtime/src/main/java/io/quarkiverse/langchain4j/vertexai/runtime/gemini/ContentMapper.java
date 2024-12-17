package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

final class ContentMapper {

    private ContentMapper() {
    }

    static GenerateContentRequest map(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            GenerationConfig generationConfig) {
        List<String> systemPrompts = new ArrayList<>();
        List<GenerateContentRequest.Content> contents = new ArrayList<>(messages.size());

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage sm) {
                systemPrompts.add(sm.text());
            } else {
                String role = RoleMapper.map(message.type());
                if (message instanceof UserMessage um) {
                    List<GenerateContentRequest.Content.Part> parts = new ArrayList<>(um.contents().size());
                    for (Content userMessageContent : um.contents()) {
                        if (userMessageContent instanceof TextContent tc) {
                            parts.add(GenerateContentRequest.Content.Part.ofText(tc.text()));
                        } else {
                            throw new IllegalArgumentException("The Gemini integration currently only supports text content");
                        }
                    }
                    contents.add(new GenerateContentRequest.Content(role, parts));
                } else if (message instanceof AiMessage am) {
                    try {
                        if (am.hasToolExecutionRequests()) {
                            List<ToolExecutionRequest> toolExecutionRequests = am.toolExecutionRequests();
                            List<FunctionCall> toolCalls = new ArrayList<>(toolExecutionRequests.size());
                            for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
                                String argumentsStr = toolExecutionRequest.arguments();
                                String name = toolExecutionRequest.name();
                                // TODO: we need to update LangChain4j to make ToolExecutionRequest use a map instead of a String
                                Map<String, Object> arguments = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                                        argumentsStr,
                                        Map.class);
                                toolCalls.add(new FunctionCall(name, arguments));
                            }
                            contents.add(new GenerateContentRequest.Content(role,
                                    List.of(new GenerateContentRequest.Content.Part(am.text(), toolCalls))));
                        } else {
                            contents.add(new GenerateContentRequest.Content(role,
                                    List.of(GenerateContentRequest.Content.Part.ofText(am.text()))));
                        }
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Unable to perform conversion of tool response", e);
                    }
                } else if (message instanceof ToolExecutionResultMessage) {
                    contents.add(new GenerateContentRequest.Content(role,
                            List.of(GenerateContentRequest.Content.Part.ofText(message.text()))));
                } else {
                    throw new IllegalArgumentException(
                            "The Gemini integration currently does not support " + message.type() + " messages");
                }
            }
        }

        return new GenerateContentRequest(contents,
                !systemPrompts.isEmpty() ? GenerateContentRequest.SystemInstruction.ofContent(systemPrompts) : null,
                toTools(toolSpecifications),
                generationConfig);
    }

    static List<GenerateContentRequest.Tool> toTools(Collection<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }
        if (toolSpecifications.isEmpty()) {
            return Collections.emptyList();
        }
        List<GenerateContentRequest.Tool> result = new ArrayList<>(toolSpecifications.size());
        for (ToolSpecification toolSpecification : toolSpecifications) {
            result.add(toTool(toolSpecification));
        }
        return result;
    }

    private static GenerateContentRequest.Tool toTool(ToolSpecification toolSpecification) {
        FunctionDeclaration.Parameters functionParameters = toFunctionParameters(toolSpecification.parameters());

        return new GenerateContentRequest.Tool(
                new FunctionDeclaration(toolSpecification.name(), toolSpecification.description(), functionParameters));
    }

    private static FunctionDeclaration.Parameters toFunctionParameters(JsonObjectSchema parameters) {
        if (parameters == null) {
            return FunctionDeclaration.Parameters.empty();
        }
        return FunctionDeclaration.Parameters.objectType(JsonSchemaElementHelper.toMap(parameters.properties()),
                parameters.required());
    }
}
