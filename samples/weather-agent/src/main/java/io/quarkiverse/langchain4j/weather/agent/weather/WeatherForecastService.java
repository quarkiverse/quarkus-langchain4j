package io.quarkiverse.langchain4j.weather.agent.weather;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

@RegisterRestClient(configKey = "openmeteo")
@Path("/v1")
public interface WeatherForecastService {

    @GET
    @Path("/forecast")
    @Tool("Forecasts the weather for the given latitude and longitude")
    @ClientQueryParam(name = "forecast_days", value = "1")
    @ClientQueryParam(name = "daily", value = {
            "temperature_2m_max",
            "temperature_2m_min",
            "precipitation_sum",
            "wind_speed_10m_max",
            "weather_code"
    })
    WeatherForecast forecast(@RestQuery double latitude, @RestQuery double longitude);

}
