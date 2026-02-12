package io.quarkiverse.langchain4j.sample.assistant.service;

import io.quarkiverse.langchain4j.sample.assistant.oauth2.McpAuthorization;

public record McpConnectionRequest(
            String name,
            TransportType transportType,
            String command,
            String url,
            McpAuthorization mcpAuthorization
    ) {
        public McpConnectionRequest(String name, TransportType transportType, String command, String url) {
            this(name, transportType, command, url, null);
        }
        
        public enum TransportType {
            STDIO,
            STREAMABLE_HTTP
        }
    }