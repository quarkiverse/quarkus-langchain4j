package io.quarkiverse.langchain4j.sample.assistant.dto;

import io.quarkiverse.langchain4j.sample.assistant.service.McpConnectionRequest;

public record McpConnection(
    String name,
    McpConnectionRequest.TransportType transportType,
    String command,
    String url
) {}
