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
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;

// server that is used for testing client-side metrics
public class metrics_mcp_server {

    @Tool
    public String foo() {
        return "foo";
    }

    @Tool
    public String protocolError() throws Exception {
        throw new RuntimeException("protocol error");
    }

    @Tool
    public ToolResponse businessError() throws Exception {
        List<TextContent> lst = new ArrayList<>();
        lst.add(new TextContent("This is an actual error"));
        return new ToolResponse(true, lst);
    }

    @Resource(uri = "file:///text-ok", mimeType = "text/plain")
    TextResourceContents resourceOk() {
        return TextResourceContents.create("file:///text", "text");
    }

    @Resource(uri = "file:///text-fail", mimeType = "text/plain")
    TextResourceContents resourceFail() {
        throw new RuntimeException("Can't read this resource!");
    }

    @Prompt
    PromptMessage prompt() {
        return PromptMessage.withUserRole(new TextContent("Hello"));
    }

    @Prompt
    PromptMessage promptFailing() {
        throw new RuntimeException("Can't get this prompt!");
    }

}
