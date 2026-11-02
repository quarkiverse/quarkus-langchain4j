///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.7.2

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class simple_mcp_server {

    @Tool(description = "Add two numbers together")
    public String add(@ToolArg(description = "First number") double a,
            @ToolArg(description = "Second number") double b) {
        return String.valueOf(a + b);
    }

    @Tool(description = "Echoes a string")
    public String echo(@ToolArg(description = "The string to echo") String input) {
        return input;
    }
}
