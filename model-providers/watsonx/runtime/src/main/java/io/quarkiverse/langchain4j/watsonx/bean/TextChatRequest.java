package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;

public record TextChatRequest(String modelId, String spaceId, String projectId, List<TextChatMessage> messages,
        List<TextChatParameterTool> tools,
        TextChatParameterTool toolChoice,
        @JsonUnwrapped TextChatParameters parameters) {
}
