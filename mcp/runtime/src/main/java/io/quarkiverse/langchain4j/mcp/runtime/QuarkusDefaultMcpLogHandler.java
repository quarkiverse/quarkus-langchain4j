package io.quarkiverse.langchain4j.mcp.runtime;

import org.jboss.logging.Logger;

import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import io.quarkiverse.langchain4j.mcp.McpClientName;
import io.quarkus.arc.Arc;

public class QuarkusDefaultMcpLogHandler implements McpLogMessageHandler {

    private static final Logger log = Logger.getLogger(QuarkusDefaultMcpLogHandler.class);

    // name of the MCP client, this will be used for value of the McpClientName qualifier
    // when generating logging events
    private final String clientName;

    public QuarkusDefaultMcpLogHandler(String clientName) {
        this.clientName = clientName;
    }

    @Override
    public void handleLogMessage(McpLogMessage message) {
        fireMessageAsCdiEvent(message);
        logMessage(message);

    }

    private void logMessage(McpLogMessage message) {
        if (message.level() == null) {
            log.warnf("Received MCP log message with unknown level: %s", message.data());
            return;
        }
        switch (message.level()) {
            case DEBUG -> log.debugf("MCP logger: %s: %s", message.logger(), message.data());
            case INFO, NOTICE -> log.infof("MCP logger: %s: %s", message.logger(), message.data());
            case WARNING -> log.warnf("MCP logger: %s: %s", message.logger(), message.data());
            case ERROR, CRITICAL, ALERT, EMERGENCY -> log.errorf("MCP logger: %s: %s", message.logger(), message.data());
        }
    }

    private void fireMessageAsCdiEvent(McpLogMessage message) {
        Arc.container().beanManager().getEvent().select(McpLogMessage.class,
                McpClientName.Literal.of(clientName)).fire(message);
    }
}
