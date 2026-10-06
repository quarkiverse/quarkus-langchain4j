package io.quarkiverse.langchain4j.mcp.test.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.common.Metadata.metadata;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * A mock MCP server using the streamable HTTP transport, backed by WireMock stubs.
 * <p>
 * A test registers only the stubs that it needs, so the server serves exactly the pieces
 * required by that test. Requests that don't match any stub are answered by WireMock
 * with a 404, which makes the client fail.
 * <p>
 * The JSON-RPC ID of every response is copied from the request using WireMock response templating.
 */
public abstract class McpMockServer<S extends McpMockServer<S>> {

    public enum ResponseFormat {
        /**
         * Every response is a single {@code application/json} object.
         */
        JSON,
        /**
         * Every response is a {@code text/event-stream} with a space after the colon in each field ({@code data: {...}}).
         */
        SSE,
        /**
         * Every response is a {@code text/event-stream} without a space after the colon in each field
         * ({@code data:{...}}). Both forms are valid according to the SSE specification, and real
         * servers such as Spring AI's emit this variant.
         */
        SSE_NO_SPACE
    }

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String METADATA_KEY = "mcpMockServerPath";
    /**
     * A placeholder for a JSON string value that is replaced by the JSON-RPC ID of the request.
     */
    protected static final String REQUEST_ID = "__REQUEST_ID__";
    private static final String REQUEST_ID_PLACEHOLDER = "\"" + REQUEST_ID + "\"";
    private static final String REQUEST_ID_TEMPLATE = "{{jsonPath request.body '$.id'}}";

    private final WireMock wireMock;
    private final String path;
    private ResponseFormat responseFormat = ResponseFormat.JSON;
    private String requiredAuthorization;

    protected McpMockServer(WireMock wireMock, String path) {
        this.wireMock = wireMock;
        this.path = path;
    }

    /**
     * A server speaking the legacy protocol (2025-11-25), which uses the {@code initialize}
     * handshake and HTTP sessions.
     */
    public static LegacyMcpMockServer legacy(WireMock wireMock, String path) {
        return new LegacyMcpMockServer(wireMock, path);
    }

    /**
     * A server speaking the modern stateless protocol (2026-07-28).
     */
    public static ModernMcpMockServer modern(WireMock wireMock, String path) {
        return new ModernMcpMockServer(wireMock, path);
    }

    /**
     * Removes all stubs previously registered for this server's path.
     */
    public S reset() {
        wireMock.removeStubsByMetadataPattern(matchingJsonPath("$." + METADATA_KEY, equalTo(path)));
        return self();
    }

    /**
     * Sets the format of responses produced by stubs registered after this call.
     */
    public S responseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
        return self();
    }

    /**
     * Makes stubs registered after this call match only requests carrying this
     * {@code Authorization} header value. All other requests are answered with a 401.
     */
    public S requireAuthorization(String authorization) {
        this.requiredAuthorization = authorization;
        register(WireMock.post(urlPathEqualTo(path))
                .atPriority(10)
                .willReturn(aResponse().withStatus(401)));
        return self();
    }

    /**
     * Stubs whatever the protocol needs before the client can start sending regular requests.
     */
    public abstract S stubInitialization();

    /**
     * Stubs whatever the client sends when its health is checked.
     */
    public abstract S stubHealthCheck();

    /**
     * Makes the health check requests fail from now on.
     */
    public abstract S failHealthCheck();

    /**
     * Stubs a {@code tools/list} request to return the given tools.
     */
    public S stubTools(McpTool... tools) {
        String toolsJson = Arrays.stream(tools)
                .map(McpTool::definition)
                .collect(Collectors.joining(",", "[", "]"));
        ObjectNode result = MAPPER.createObjectNode();
        result.set("tools", parse(toolsJson));
        register(request("tools/list").willReturn(response(result)));
        return self();
    }

    /**
     * Stubs a {@code tools/call} request for the given tool with exactly the given arguments
     * to return a single text content.
     */
    public S stubToolCall(String toolName, String expectedArgumentsJson, String resultText) {
        register(toolCall(toolName)
                .withRequestBody(matchingJsonPath("$.params.arguments", equalToJson(expectedArgumentsJson)))
                .willReturn(response(textToolResult(resultText))));
        return self();
    }

    /**
     * Stubs a {@code tools/call} request for the given tool, with any arguments, to return
     * a single text content only after the given delay.
     */
    public S stubSlowToolCall(String toolName, Duration delay, String resultText) {
        register(toolCall(toolName)
                .willReturn(response(textToolResult(resultText))
                        .withFixedDelay((int) delay.toMillis())));
        return self();
    }

    /**
     * Makes requests with the given method fail with the given HTTP status and an empty body from now on,
     * overriding any stub registered for that method before.
     */
    public S stubError(String method, int httpStatus) {
        register(request(method).atPriority(1).willReturn(aResponse().withStatus(httpStatus)));
        return self();
    }

    @SuppressWarnings("unchecked")
    protected S self() {
        return (S) this;
    }

    protected MappingBuilder toolCall(String toolName) {
        return request("tools/call")
                .withRequestBody(matchingJsonPath("$.params.name", equalTo(toolName)));
    }

    /**
     * Creates a matcher for a JSON-RPC message with the given method sent to this server.
     * Subclasses add the requirements specific to their protocol version.
     */
    protected MappingBuilder request(String method) {
        return post()
                .withRequestBody(matchingJsonPath("$.method", equalTo(method)));
    }

    /**
     * Creates a matcher for any POST sent to this server, honouring {@link #requireAuthorization(String)}.
     */
    protected MappingBuilder post() {
        MappingBuilder builder = WireMock.post(urlPathEqualTo(path));
        if (requiredAuthorization != null) {
            builder.withHeader("Authorization", equalTo(requiredAuthorization));
        }
        return builder;
    }

    protected void register(MappingBuilder builder) {
        wireMock.register(builder.withMetadata(metadata().attr(METADATA_KEY, path)));
    }

    /**
     * Builds a response carrying the given JSON-RPC result, in the configured response format.
     */
    protected ResponseDefinitionBuilder response(ObjectNode result) {
        ObjectNode message = MAPPER.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", REQUEST_ID);
        message.set("result", result);
        if (responseFormat == ResponseFormat.JSON) {
            return aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(toTemplatedJson(message));
        }
        return sseResponse(responseFormat, message);
    }

    /**
     * Builds an SSE response carrying the given JSON-RPC messages as separate events.
     * If the response format is JSON, the regular SSE format is used.
     */
    protected ResponseDefinitionBuilder sseResponse(ObjectNode... messages) {
        return sseResponse(responseFormat == ResponseFormat.SSE_NO_SPACE ? ResponseFormat.SSE_NO_SPACE : ResponseFormat.SSE,
                messages);
    }

    private ResponseDefinitionBuilder sseResponse(ResponseFormat format, ObjectNode... messages) {
        String separator = format == ResponseFormat.SSE_NO_SPACE ? ":" : ": ";
        StringBuilder body = new StringBuilder();
        for (ObjectNode message : messages) {
            if (format == ResponseFormat.SSE_NO_SPACE && message.has("id")) {
                body.append("id").append(separator).append(REQUEST_ID_TEMPLATE).append("\n");
            }
            body.append("event").append(separator).append("message\n");
            body.append("data").append(separator).append(toTemplatedJson(message)).append("\n\n");
        }
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(body.toString());
    }

    protected static ObjectNode textToolResult(String text) {
        ObjectNode result = MAPPER.createObjectNode();
        result.putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", text);
        return result;
    }

    protected static ObjectNode notification(String method, ObjectNode params) {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);
        return notification;
    }

    /**
     * Serializes the message into a single line, replacing every occurrence of the
     * {@link #REQUEST_ID} placeholder by a template that resolves to the ID of the request.
     * The template is followed by a space, because Handlebars can't parse it when it's directly
     * followed by a closing brace of the JSON object.
     */
    protected static String toTemplatedJson(ObjectNode message) {
        try {
            return MAPPER.writeValueAsString(message).replace(REQUEST_ID_PLACEHOLDER, REQUEST_ID_TEMPLATE + " ");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
