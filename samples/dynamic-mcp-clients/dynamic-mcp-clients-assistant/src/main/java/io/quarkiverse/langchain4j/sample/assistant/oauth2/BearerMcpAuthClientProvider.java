package io.quarkiverse.langchain4j.sample.assistant.oauth2;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;

public class BearerMcpAuthClientProvider implements McpClientAuthProvider {

    final String token;
    public BearerMcpAuthClientProvider(String token) {
        this.token = token;
    }
    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + token;
    }

}
