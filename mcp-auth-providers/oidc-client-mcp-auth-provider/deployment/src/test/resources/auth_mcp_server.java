///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import java.time.Duration;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.smallrye.mutiny.Uni;

public class auth_mcp_server {

    @Inject
    CurrentVertxRequest request;

    @Tool(description = "Returns the client's authentication token")
    public String getToken() {
        return request.getCurrent().request().headers().get("Authorization");
    }

    @Tool(description = "Requests the client's roots")
    public Uni<String> readRoots(Roots roots) {
        return roots.list().map(list -> list.stream().map(root -> root.uri()).collect(Collectors.joining(", ")));
    }

    @Tool(description = "Requests roots after the client's short-lived token expires")
    public Uni<String> readRootsAfterTokenExpires(Roots roots) {
        return Uni.createFrom().voidItem().onItem().delayIt().by(Duration.ofSeconds(2))
                .chain(() -> readRoots(roots));
    }

}
