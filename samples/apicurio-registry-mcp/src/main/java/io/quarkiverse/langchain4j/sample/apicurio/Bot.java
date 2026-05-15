package io.quarkiverse.langchain4j.sample.apicurio;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;

@RegisterAiService(tools = ApicurioRegistryMcpTools.class)
public interface Bot {

    @SystemMessage("""
            You are an intelligent assistant with access to Apicurio Registry.
            You can search for MCP servers in the registry, connect to them,
            and then use their tools to help the user.

            When a user asks for help, first search the registry for relevant
            MCP servers, connect to them, and then use their tools.
            """)
    String chat(String message);
}
