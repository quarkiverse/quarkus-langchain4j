package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

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
                    if (am.hasToolExecutionRequests()) {
                        throw new IllegalArgumentException("The Gemini integration currently does not support tools");
                    }
                    contents.add(new GenerateContentRequest.Content(role,
                            List.of(GenerateContentRequest.Content.Part.ofText(am.text()))));
                } else {
                    throw new IllegalArgumentException(
                            "The Gemini integration currently does not support " + message.type() + " messages");
                }
            }
        }

        List<GenerateContentRequest.Tool> tools;
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            tools = null;
        } else {
            tools = new ArrayList<>(toolSpecifications.size());
            for (GenerateContentRequest.Tool tool : tools) {
                // TODO: implement
            }
        }

        return new GenerateContentRequest(contents,
                !systemPrompts.isEmpty() ? GenerateContentRequest.SystemInstruction.ofContent(systemPrompts) : null, tools,
                generationConfig);
    }
}
