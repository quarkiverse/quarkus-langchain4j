package io.quarkiverse.langchain4j.mcp.runtime.devui;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Any;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.devui.json.JsonSchemaToExampleStringHelper;
import io.quarkiverse.langchain4j.mcp.runtime.devui.json.McpClientInfo;
import io.quarkiverse.langchain4j.mcp.runtime.devui.json.McpToolInfo;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;

public class McpClientsJsonRpcService {

    // The key here is the logical (CDI) name of the client
    private final Map<String, McpClient> clients = new HashMap<>();

    public McpClientsJsonRpcService() {
        // initialize the client map
        for (InstanceHandle<McpClient> handle : Arc.container().select(McpClient.class, Any.Literal.INSTANCE).handles()) {
            InjectableBean<McpClient> bean = handle.getBean();
            String key = null;
            for (Annotation qualifier : bean.getQualifiers()) {
                if (qualifier instanceof McpClientName mcpClientName) {
                    key = mcpClientName.value();
                    break;
                }
            }
            // TODO: if no CDI key exists, generate one ad-hoc for the purpose of having the client uniquely identifiable in the UI?
            clients.put(key == null ? "null" : key, handle.get());
        }
    }

    public List<McpClientInfo> clientInfos() {
        List<McpClientInfo> infos = new ArrayList<>();
        for (String key : clients.keySet()) {
            McpClient client = clients.get(key);
            McpClientInfo info = buildClientInfo(client);
            info.setCdiName(key);
            infos.add(info);
        }
        return infos;
    }

    public String executeTool(String clientName, String toolName, String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(arguments)
                .build();
        return clients.get(clientName).executeTool(request).resultText();
    }

    private McpClientInfo buildClientInfo(McpClient client) {
        McpClientInfo info = new McpClientInfo();
        info.setTools(client.listTools().stream().map(this::buildToolInfo).toList());
        return info;
    }

    private McpToolInfo buildToolInfo(ToolSpecification toolSpec) {
        McpToolInfo info = new McpToolInfo();
        info.setName(toolSpec.name());
        info.setDescription(toolSpec.description());
        info.setExampleInput(JsonSchemaToExampleStringHelper.generateExampleStringFromSchema(toolSpec.parameters()));
        return info;
    }
}
