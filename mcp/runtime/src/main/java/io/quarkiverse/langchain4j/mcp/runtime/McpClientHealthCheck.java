package io.quarkiverse.langchain4j.mcp.runtime;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.inject.Any;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;

@Readiness
public class McpClientHealthCheck implements HealthCheck {

    private final Map<String, McpClient> clientMap;

    public McpClientHealthCheck() {
        clientMap = getClientMap();
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name("MCP clients health check")
                .up();
        for (String name : clientMap.keySet()) {
            McpClient client = clientMap.get(name);
            try {
                client.checkHealth();
                builder.withData(name, "OK");
            } catch (Exception e) {
                builder.down().withData(name, e.getMessage());
            }
        }
        return builder.build();
    }

    private Map<String, McpClient> getClientMap() {
        Map<String, McpClient> map = new HashMap<>();
        for (InstanceHandle<McpClient> handle : Arc.container().select(McpClient.class, Any.Literal.INSTANCE).handles()) {
            InjectableBean<McpClient> bean = handle.getBean();
            for (Annotation qualifier : bean.getQualifiers()) {
                if (qualifier instanceof McpClientName q) {
                    String name = q.value() != null && !q.value().isEmpty() ? q.value() : "<default>";
                    map.put(name, handle.get());
                }
            }
        }
        return map;
    }
}
