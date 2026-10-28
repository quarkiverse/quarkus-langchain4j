package io.quarkiverse.langchain4j.sample.registry.weather;

import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

@ApplicationScoped
public class WeatherTools {
    private static final Logger LOG = Logger.getLogger(WeatherTools.class);
    private static final Map<String, String> WEATHER = Map.of(
            "amsterdam", "Amsterdam: 16 C, light rain",
            "paris", "Paris: 21 C, sunny",
            "madrid", "Madrid: 27 C, sunny");

    @Tool(description = "Get demonstration weather for Amsterdam, Paris or Madrid. Data is fictional, not a live forecast.")
    public String getWeather(@ToolArg(description = "City name: Amsterdam, Paris or Madrid") String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("A city is required");
        }
        String forecast = WEATHER.get(city.trim().toLowerCase(Locale.ROOT));
        if (forecast == null) {
            return "No sample weather for this city. Choose Amsterdam, Paris or Madrid.";
        }
        LOG.infof("Sample MCP getWeather invoked: %s", forecast);
        return "Fictional sample data: " + forecast;
    }
}
