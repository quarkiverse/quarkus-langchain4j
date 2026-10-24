package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.Usage;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

// fields rather than accessors are serialized, so an unset finishReason stays absent from the JSON
public record ChatReplyImpl(String text, List<ToolCall> toolCalls, Usage usage, FinishReason finishReason, String thinking,
        String modelName, String id, Duration delay, List<String> chunks) implements ChatReply {

    public static final ChatReplyImpl EMPTY = new ChatReplyImpl(null, null, null, null, null, null, null, null, null);

    @Override
    public ChatReply addToolCall(String name, Map<String, Object> args) {
        try {
            return addToolCall(name, MockApiJson.mapper().writeValueAsString(args));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize tool call arguments", e);
        }
    }

    @Override
    public ChatReply addToolCall(String name, String argumentsJson) {
        List<ToolCall> calls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
        calls.add(ToolCall.of(name, argumentsJson));
        return new ChatReplyImpl(text, List.copyOf(calls), usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply usage(int in, int out) {
        return new ChatReplyImpl(text, toolCalls, Usage.of(in, out), finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply finishReason(FinishReason finishReason) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply thinking(String thinking) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply modelName(String modelName) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply id(String id) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply delay(Duration delay) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public ChatReply chunks(List<String> chunks) {
        return new ChatReplyImpl(text, toolCalls, usage, finishReason, thinking, modelName, id, delay, chunks);
    }

    @Override
    public FinishReason finishReason() {
        if (finishReason != null) {
            return finishReason;
        }
        return toolCalls != null && !toolCalls.isEmpty() ? FinishReason.TOOL_CALLS : FinishReason.STOP;
    }
}
