package io.quarkiverse.langchain4j.mcp.runtime;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.inject.Any;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.jackson.JacksonBasicMessageBodyReader;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientRuntimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.http.McpMicroProfileHealthCheck;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.json.JsonObject;

@Readiness
public class McpClientHealthCheck implements HealthCheck {
    private static final Logger log = Logger.getLogger(McpClientHealthCheck.class);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final Map<String, McpClient> clientMap;
    private final Map<String, McpMicroProfileHealthCheck> microProfileHealthChecks;

    public McpClientHealthCheck(McpRuntimeConfiguration mcpRuntimeConfig) {
        clientMap = getClientMap();
        microProfileHealthChecks = getMicroProfileHealthChecks(mcpRuntimeConfig.clients());
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name("MCP clients health check")
                .up();
        for (String name : clientMap.keySet()) {
            try {
                String microProfileStatus = checkMicroProfileHealth(name);
                if (microProfileStatus == null) {
                    McpClient client = clientMap.get(name);
                    client.checkHealth();
                    builder.withData(name, "OK");
                } else if (UP.equalsIgnoreCase(microProfileStatus)) {
                    builder.withData(name, "OK");
                } else if (DOWN.equalsIgnoreCase(microProfileStatus)) {
                    builder.down().withData(name, "Down");
                }
            } catch (Exception e) {
                builder.down().withData(name, e.getMessage());
            }
        }
        return builder.build();
    }

    private static Map<String, McpClient> getClientMap() {
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

    private static Map<String, McpMicroProfileHealthCheck> getMicroProfileHealthChecks(
            Map<String, McpClientRuntimeConfig> clients) {
        Map<String, McpMicroProfileHealthCheck> map = new HashMap<>();
        for (Map.Entry<String, McpClientRuntimeConfig> entry : clients.entrySet()) {
            if (entry.getValue().url().isPresent() && entry.getValue().microprofileHealthCheck()) {
                McpMicroProfileHealthCheck microProfileHealthCheck = QuarkusRestClientBuilder.newBuilder()
                        .baseUri(buildHealthUri(entry.getKey(), entry.getValue().url().get(),
                                entry.getValue().microprofileHealthCheckPath()))
                        .register(new JacksonBasicMessageBodyReader(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER))
                        .build(McpMicroProfileHealthCheck.class);
                map.put(entry.getKey(), microProfileHealthCheck);
            }
        }
        return map;
    }

    private static URI buildHealthUri(String name, String mcpServerUrl, String microProfileHealthCheckPath) {
        try {
            URI uri = URI.create(mcpServerUrl).resolve(microProfileHealthCheckPath);
            log.debugf("MCP client %s health URI: %s", name, uri.toString());
            return uri;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String checkMicroProfileHealth(String name) {
        McpMicroProfileHealthCheck microProfileHealthCheck = microProfileHealthChecks.get(name);
        if (microProfileHealthCheck == null) {
            log.debugf("MCP client %s microprofile health check client is not available", name);
            return null;
        }
        try {
            JsonObject health = new JsonObject(microProfileHealthCheck.healthCheck());
            //TODO: This is a general server status - drill down into MCP specific checks once they become supported
            String status = health.getString("status");
            log.debugf("MCP client %s microprofile health check status is %s", name, status);
            return status;
        } catch (WebApplicationException ex) {
            if (ex.getResponse().getStatus() == 404) {
                log.debugf("MCP client %s microprofile health check endpoint does not exist", name);
                return null;
            }
            throw ex;
        }
    }
}
