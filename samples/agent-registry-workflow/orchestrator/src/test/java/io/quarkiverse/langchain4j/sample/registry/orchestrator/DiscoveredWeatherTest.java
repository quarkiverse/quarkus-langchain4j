package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;

class DiscoveredWeatherTest {
    @Test
    void serializesToolArgumentsAndConnectsBeforeInvocation() throws Exception {
        DiscoveredWeather weather = new DiscoveredWeather();
        weather.tools = mock(ApicurioRegistryMcpTools.class);
        weather.mapper = new ObjectMapper();
        String city = "a city with \"quotes\"";
        String arguments = weather.mapper.writeValueAsString(Map.of("city", city));
        when(weather.tools.searchMcpServers("weather")).thenReturn("- **weather-tools/weather** (weather)");
        when(weather.tools.connectMcpServer("weather", "weather-tools")).thenReturn("Connected to MCP server");
        when(weather.tools.callMcpTool("weather-tools/weather", "getWeather", arguments)).thenReturn("fixture");

        assertEquals("fixture", weather.weatherForCity(city));
        var order = inOrder(weather.tools);
        order.verify(weather.tools).searchMcpServers("weather");
        order.verify(weather.tools).connectMcpServer("weather", "weather-tools");
        order.verify(weather.tools).callMcpTool("weather-tools/weather", "getWeather", arguments);
    }

    @Test
    void doesNotConnectWhenTheRequiredRegistrationIsAbsent() {
        DiscoveredWeather weather = new DiscoveredWeather();
        weather.tools = mock(ApicurioRegistryMcpTools.class);
        when(weather.tools.searchMcpServers("weather")).thenReturn("No MCP servers found");
        assertThrows(IllegalStateException.class, () -> weather.weatherForCity("Amsterdam"));
        verify(weather.tools).searchMcpServers("weather");
        verifyNoMoreInteractions(weather.tools);
    }
}
