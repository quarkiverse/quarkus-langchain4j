///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:1.7.2

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;

public class auth_mcp_server {

    @Inject
    CurrentVertxRequest request;

    @Tool(description = "Returns the client's authentication token")
    public String getToken() {
        return request.getCurrent().request().headers().get("Authorization");
    }

}
