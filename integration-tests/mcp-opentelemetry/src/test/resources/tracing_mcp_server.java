///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.7.2

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;

public class tracing_mcp_server {

    @Tool(description = "Echoes the value of a _meta field")
    public String echoMeta(Meta meta, @ToolArg(description = "The key to look up in _meta") String key) {
        Object value = meta.asJsonObject().getValue(key);
        return value != null ? value.toString() : "null";
    }

    @Tool(description = "Returns an error response")
    public ToolResponse errorResponse() {
        return new ToolResponse(true, List.of(new TextContent("Something went wrong")));
    }

    @Tool(description = "Takes a long time to complete")
    public String slowOperation() throws InterruptedException {
        TimeUnit.MINUTES.sleep(1);
        return "done";
    }

    @Resource(uri = "file:///greeting", description = "A greeting", mimeType = "text/plain")
    TextResourceContents greeting() {
        return TextResourceContents.create("file:///greeting", "Hello from resource");
    }

    @Prompt(description = "A simple greeting prompt")
    PromptMessage greeting_prompt() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt"));
    }
}
