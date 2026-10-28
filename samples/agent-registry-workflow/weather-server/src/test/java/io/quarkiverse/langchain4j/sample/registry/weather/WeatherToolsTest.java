package io.quarkiverse.langchain4j.sample.registry.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WeatherToolsTest {
    @Test
    void normalizesTheCityWithoutInventingUnknownForecasts() {
        WeatherTools tools = new WeatherTools();
        assertEquals("Fictional sample data: Amsterdam: 16 C, light rain", tools.getWeather(" AMSTERDAM "));
        assertEquals("No sample weather for this city. Choose Amsterdam, Paris or Madrid.", tools.getWeather("unknown"));
        assertThrows(IllegalArgumentException.class, () -> tools.getWeather(" "));
    }
}
