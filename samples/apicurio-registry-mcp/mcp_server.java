///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.40.1.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.4

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class mcp_server {

    @Tool(description = "Add two numbers together")
    public String add(@ToolArg(description = "First number") double a,
            @ToolArg(description = "Second number") double b) {
        return String.valueOf(a + b);
    }

    @Tool(description = "Multiply two numbers together")
    public String multiply(@ToolArg(description = "First number") double a,
            @ToolArg(description = "Second number") double b) {
        return String.valueOf(a * b);
    }

    @Tool(description = "Get the current date and time")
    public String currentTime() {
        return java.time.LocalDateTime.now().toString();
    }
}
