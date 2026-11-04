package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;

/** A typed application tool around the extension's generic discovery/connection tools. */
@ApplicationScoped
public class DiscoveredWeather {
    @Inject
    ApicurioRegistryMcpTools tools;

    @Inject
    ObjectMapper mapper;

    @Tool("Get fictional sample weather for Amsterdam, Paris or Madrid from the registered Weather MCP server")
    public String weatherForCity(@P("City name") String city) throws JsonProcessingException {
        // This application selects a known artifact role, just as the A2A flow selects its agent roles.
        // Only Registry knows the network endpoint. Never construct an endpoint from model output.
        String matches = tools.searchMcpServers("weather");
        if (!matches.contains("**weather-tools/weather**")) {
            throw new IllegalStateException("Weather server is not registered");
        }
        String connection = tools.connectMcpServer("weather", "weather-tools");
        if (!connection.startsWith("Connected to") && !connection.startsWith("Already connected")) {
            throw new IllegalStateException("Weather server could not be connected");
        }
        return tools.callMcpTool("weather-tools/weather", "getWeather", mapper.writeValueAsString(Map.of("city", city)));
    }
}
