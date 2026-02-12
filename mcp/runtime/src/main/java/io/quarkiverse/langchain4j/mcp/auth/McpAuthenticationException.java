package io.quarkiverse.langchain4j.mcp.auth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class McpAuthenticationException extends RuntimeException {
    private static Pattern pattern = Pattern.compile("resource_metadata=[\"']([^\"']+)[\"']");

    private final int statusCode;
    private final String resourceMetadata;

    public McpAuthenticationException(int statusCode, String wwwAuthenticate) {
        super("Authentication required: HTTP " + statusCode);
        this.statusCode = statusCode;
        this.resourceMetadata = extractResourceMetadata(wwwAuthenticate);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResourceMetadata() {
        return resourceMetadata;
    }

    private String extractResourceMetadata(String wwwAuthenticate) {
        if (wwwAuthenticate == null) {
            return null;
        }

        // Parse WWW-Authenticate header for resource_metadata parameter
        // Example: Bearer realm="example", resource_metadata="https://example.com/.well-known/oauth"
        Matcher matcher = pattern.matcher(wwwAuthenticate);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}