package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTools;

public record TextChatRequest(String modelId, String projectId, List<TextChatMessage> messages,
        List<TextChatParameterTools> tools,
        @JsonUnwrapped TextChatParameters parameters) {
}
