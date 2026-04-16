package io.quarkiverse.langchain4j.mcp.runtime.apicurio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents an MCP server definition as stored in Apicurio Registry.
 * This is the content of an MCP_TOOL artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpServerDefinition(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("url") String url,
        @JsonProperty("transportType") String transportType) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static McpServerDefinition fromJson(String json) {
        try {
            return MAPPER.readValue(json, McpServerDefinition.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MCP server definition: " + e.getMessage(), e);
        }
    }
}
