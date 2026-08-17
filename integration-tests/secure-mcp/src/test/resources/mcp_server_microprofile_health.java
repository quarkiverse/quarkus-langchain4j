///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkus:quarkus-smallrye-health
//DEPS io.quarkus:quarkus-oidc
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.4.0
//Q:CONFIG quarkus.mcp.server.client-logging.default-level=DEBUG
//Q:CONFIG quarkus.http.auth.permission.mcp.paths=/mcp/*
//Q:CONFIG quarkus.http.auth.permission.mcp.policy=authenticated
//Q:CONFIG quarkus.oidc.public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlivFI8qB4D0y2jy0CfEqFyy46R0o7S8TKpsx5xbHKoU1VWg6QkQm+ntyIv1p4kE1sPEQO73+HY8+Bzs75XwRTYL1BmR1w8J5hmjVWjc6R2BTBGAYRPFRhor3kpM6ni2SPmNNhurEAHw7TaqszP5eUF/F9+KEBWkwVta+PZ37bwqSE4sCb1soZFrVz/UT/LF4tYpuVYt3YbqToZ3pZOZ9AX2o1GCG3xwOjkc4x0W7ezbQZdC9iftPxVHR8irOijJRRjcPDtA6vPKpzLl6CyYnsIYPd99ltwxTHjr3npfv/3Lw50bAkbT4HeLFxTx4flEoZLKO/g0bAoV2uqBhkA9xnQIDAQAB

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.security.identity.SecurityIdentity;

public class mcp_server_microprofile_health {

    @Inject 
    SecurityIdentity identity;
    
    @Tool
    public String getUserName() {
        return identity.getPrincipal().getName();
    }

}
