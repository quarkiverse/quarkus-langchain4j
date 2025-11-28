///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:1.7.2
//Q:CONFIG quarkus.mcp.server.client-logging.default-level=DEBUG

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;

// Very basic server used for testing TLS. It just has a single tool.
public class tls_mcp_server {

    @Tool(description = "Echoes a string")
    public String echoString(@ToolArg(description = "The string to be echoed") String input) {
        return input;
    }

}
