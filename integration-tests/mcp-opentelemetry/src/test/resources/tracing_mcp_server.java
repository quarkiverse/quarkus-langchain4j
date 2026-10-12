///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.3.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.3
//Q:CONFIG quarkus.mcp.server.client-logging.default-level=DEBUG

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

public class tracing_mcp_server {

    @Inject
    ResourceManager resourceManager;

    @Inject
    ToolManager toolManager;

    private final AtomicReference<String> statusValue = new AtomicReference<>("initial");
    private final AtomicInteger registeredToolCounter = new AtomicInteger();

    @Tool(description = "Echoes the value of a _meta field")
    public String echoMeta(Meta meta, @ToolArg(description = "The key to look up in _meta") String key) {
        Object value = meta.asJsonObject().getValue(key);
        return value != null ? value.toString() : "null";
    }

    @Tool(description = "Returns an error response")
    public ToolResponse errorResponse() {
        return new ToolResponse(true, List.of(new TextContent("Something went wrong")));
    }

    @Tool(description = "Fails with a JSON-RPC error")
    public String protocolError() {
        throw new McpException("Internal failure", -32603);
    }

    @Tool(description = "Takes a long time to complete")
    public String slowOperation() throws InterruptedException {
        TimeUnit.MINUTES.sleep(1);
        return "done";
    }

    @Tool(description = "Updates the status resource and notifies its subscribers")
    public String updateStatus(String newValue) {
        statusValue.set(newValue);
        resourceManager.getResource("file:///status").sendUpdateAndForget();
        return "OK";
    }

    @Tool(description = "Registers a new tool, which makes the server notify the client that the tool list changed")
    public String registerNewTool() {
        toolManager.newTool("registeredTool" + registeredToolCounter.incrementAndGet())
                .setDescription("A tool registered at runtime")
                .setHandler(args -> ToolResponse.success("OK"))
                .register();
        return "OK";
    }

    @Tool(description = "Sends a log message to the client")
    public String logMessage(McpLog log) {
        log.info("Hello from the server");
        return "OK";
    }

    @Resource(uri = "file:///greeting", description = "A greeting", mimeType = "text/plain")
    TextResourceContents greeting() {
        return TextResourceContents.create("file:///greeting", "Hello from resource");
    }

    @Resource(uri = "file:///status", description = "A status resource", mimeType = "text/plain")
    TextResourceContents status() {
        return TextResourceContents.create("file:///status", statusValue.get());
    }

    @ResourceTemplate(uriTemplate = "file:///greeting/{name}", description = "A personalized greeting", mimeType = "text/plain")
    TextResourceContents personalizedGreeting(String name, RequestUri uri) {
        return TextResourceContents.create(uri.value(), "Hello " + name);
    }

    @Prompt(description = "A simple greeting prompt")
    PromptMessage greeting_prompt() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt"));
    }

    // asks the client for its roots, so that the client receives a server-initiated roots/list request
    @Notification(Type.ROOTS_LIST_CHANGED)
    void rootsListChanged(Roots roots) {
        roots.listAndAwait();
    }
}
