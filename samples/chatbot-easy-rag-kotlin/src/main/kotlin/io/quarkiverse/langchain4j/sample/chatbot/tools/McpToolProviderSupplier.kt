package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.McpClient
import dev.langchain4j.service.tool.ToolProvider
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.function.Supplier

@ApplicationScoped
class McpToolProviderSupplier : Supplier<ToolProvider> {

    @Inject
    @McpClientName("time")
    private lateinit var timeClient : McpClient

    override fun get(): ToolProvider {
        return McpToolProvider.builder()
            .mcpClients(timeClient)
            .build()
    }

}
