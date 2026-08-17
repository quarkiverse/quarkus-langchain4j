///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;

public class auth_mcp_server {

    @Inject
    CurrentVertxRequest request;

    @Tool(description = "Returns the client's authentication token")
    public String getToken() {
        return request.getCurrent().request().headers().get("Authorization");
    }

}
