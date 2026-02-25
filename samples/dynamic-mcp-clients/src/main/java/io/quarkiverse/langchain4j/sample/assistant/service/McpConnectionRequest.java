package io.quarkiverse.langchain4j.sample.assistant.service;

public record McpConnectionRequest(
            String name,
            TransportType transportType,
            String command,
            String url
    ) {
        public enum TransportType {
            STDIO,
            STREAMABLE_HTTP
        }
    }