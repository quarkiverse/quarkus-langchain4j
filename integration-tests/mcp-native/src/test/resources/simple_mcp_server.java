///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.31.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.9.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.9.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:1.9.0

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;

public class simple_mcp_server {

    @Tool(description = "Echoes a string")
    public String echoString(@ToolArg(description = "The string to be echoed") String input) {
        return input;
    }

}
