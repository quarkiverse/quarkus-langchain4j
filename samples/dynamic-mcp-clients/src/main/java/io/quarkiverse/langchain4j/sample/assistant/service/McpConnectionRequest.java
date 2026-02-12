package io.quarkiverse.langchain4j.sample.assistant.service;

public record McpConnectionRequest(
            String name,
            TransportType transportType,
            String command,
            String url,
            String accessToken
    ) {
        public McpConnectionRequest(String name, TransportType transportType, String command, String url) {
            this(name, transportType, command, url, null);
        }
        
        public enum TransportType {
            STDIO,
            STREAMABLE_HTTP
        }
    }