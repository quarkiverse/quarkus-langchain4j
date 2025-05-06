package io.quarkiverse.langchain4j.gemini.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.internal.CustomMimeTypesFileTypeDetector;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public final class ContentMapper {

    private static final CustomMimeTypesFileTypeDetector mimeTypeDetector = new CustomMimeTypesFileTypeDetector();

    private ContentMapper() {
    }

    public static GenerateContentRequest map(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            GenerationConfig generationConfig) {
        List<String> systemPrompts = new ArrayList<>();
        List<Content> contents = new ArrayList<>(messages.size());

        Map<String, Content> functionCalls = new HashMap<>();
        Map<String, Content> functionResponses = new HashMap<>();

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage sm) {
                systemPrompts.add(sm.text());
            } else {
                String role = RoleMapper.map(message.type());
                if (message instanceof UserMessage um) {
                    List<Content.Part> parts = new ArrayList<>(um.contents().size());
                    for (dev.langchain4j.data.message.Content userMessageContent : um.contents()) {
                        if (userMessageContent instanceof TextContent tc) {
                            parts.add(Content.Part.ofText(tc.text()));
                        } else if (userMessageContent instanceof ImageContent ic) {
                            Image image = ic.image();
                            URI uri = image.url();
                            if (uri != null) {
                                parts.add(Content.Part
                                        .ofFileData(new FileData(mimeTypeDetector.probeContentType(uri), uri.toString())));
                            } else {
                                parts.add(Content.Part
                                        .ofInlineData(new Blob(image.mimeType(), image.base64Data())));
                            }
                        } else if (userMessageContent instanceof PdfFileContent fc) {
                            URI uri = fc.pdfFile().url();
                            if (uri != null) {
                                parts.add(Content.Part
                                        .ofFileData(new FileData(mimeTypeDetector.probeContentType(uri), uri.toString())));
                            } else {
                                parts.add(Content.Part
                                        .ofInlineData(new Blob("application/pdf", fc.pdfFile().base64Data())));
                            }
                        } else if (userMessageContent instanceof VideoContent vc) {
                            Video video = vc.video();
                            URI uri = video.url();
                            if (uri != null) {
                                parts.add(Content.Part
                                        .ofFileData(new FileData(mimeTypeDetector.probeContentType(uri), uri.toString())));
                            } else {
                                parts.add(Content.Part
                                        .ofInlineData(new Blob(video.mimeType(), video.base64Data())));
                            }
                        } else if (userMessageContent instanceof AudioContent ac) {
                            Audio audio = ac.audio();
                            URI uri = audio.url();
                            if (uri != null) {
                                parts.add(Content.Part
                                        .ofFileData(new FileData(mimeTypeDetector.probeContentType(uri), uri.toString())));
                            } else {
                                parts.add(Content.Part
                                        .ofInlineData(new Blob(audio.mimeType(), audio.base64Data())));
                            }
                        } else {
                            throw new IllegalArgumentException("The Gemini integration currently only supports text content");
                        }
                    }
                    contents.add(new Content(role, parts));
                } else if (message instanceof AiMessage am) {
                    try {
                        if (am.hasToolExecutionRequests()) {
                            for (ToolExecutionRequest toolExecutionRequest : am.toolExecutionRequests()) {
                                String argumentsStr = toolExecutionRequest.arguments();
                                String name = toolExecutionRequest.name();
                                Map<String, Object> arguments = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                                        argumentsStr,
                                        Map.class);
                                FunctionCall functionCall = new FunctionCall(name, arguments);
                                Content.Part part = Content.Part
                                        .ofFunctionCall(functionCall);
                                functionCalls.put(name, new Content(role, List.of(part)));
                            }
                        } else {
                            contents.add(new Content(role,
                                    List.of(Content.Part.ofText(am.text()))));
                        }
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Unable to perform conversion of tool response", e);
                    }
                } else if (message instanceof ToolExecutionResultMessage toolExecResult) {
                    String toolName = toolExecResult.toolName();
                    Object content = toolExecResult.text();
                    FunctionResponse functionResponse = new FunctionResponse(toolName,
                            new FunctionResponse.Response(toolName, content));

                    Content.Part part = Content.Part
                            .ofFunctionResponse(functionResponse);
                    functionResponses.put(toolName, new Content(role, List.of(part)));
                } else {
                    throw new IllegalArgumentException(
                            "The Gemini integration currently does not support " + message.type() + " messages");
                }
            }
        }

        for (Map.Entry<String, Content> entry : functionCalls.entrySet()) {
            contents.add(entry.getValue());
            if (functionResponses.containsKey(entry.getKey())) {
                contents.add(functionResponses.get(entry.getKey()));
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
        List<FunctionDeclaration> functionDeclarations = new ArrayList<>(toolSpecifications.size());
        for (ToolSpecification toolSpecification : toolSpecifications) {
            functionDeclarations.add(toFunctionDeclaration(toolSpecification));
        }
        return List.of(new GenerateContentRequest.Tool(functionDeclarations));
    }

    private static FunctionDeclaration toFunctionDeclaration(ToolSpecification toolSpecification) {
        FunctionDeclaration.Parameters functionParameters = toFunctionParameters(toolSpecification.parameters());

        return new FunctionDeclaration(toolSpecification.name(), toolSpecification.description(), functionParameters);
    }

    private static FunctionDeclaration.Parameters toFunctionParameters(JsonObjectSchema parameters) {
        if (parameters == null) {
            return FunctionDeclaration.Parameters.empty();
        }
        return FunctionDeclaration.Parameters.objectType(JsonSchemaElementUtils.toMap(parameters.properties()),
                parameters.required());
    }
}
