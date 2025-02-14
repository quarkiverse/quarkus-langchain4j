package io.quarkiverse.langchain4j.sample;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.qute.Qute;
import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;


public class Weather {

    @RestClient
    WeatherClient weatherClient;

    @Tool(description = "Get weather alerts for a US state.")
    String getAlerts(@ToolArg(description = "Two-letter US state code (e.g. CA, NY)") String state) {
        return formatAlerts(weatherClient.getAlerts(state));
    }

    @Tool(description = "Get weather forecast for a location.")
    String getForecast(@ToolArg(description = "Latitude of the location") double latitude,
                       @ToolArg(description = "Longitude of the location") double longitude) {
        var points = weatherClient.getPoints(latitude, longitude);
        var url = Qute.fmt("{p.properties.forecast}", Map.of("p", points));

        return formatForecast(weatherClient.getForecast(url));
    }

    String formatForecast(Forecast forecast) {
        return forecast.properties().periods().stream().map(period -> {

            return Qute.fmt(
                    """
                            Temperature: {p.temperature}°{p.temperatureUnit}
                            Wind: {p.windSpeed} {p.windDirection}
                            Forecast: {p.detailedForecast}
                            """,
                    Map.of("p", period));
        }).collect(Collectors.joining("\n---\n"));
    }

    String formatAlerts(Alerts alerts) {
        return alerts.features().stream().map(feature -> {
            return Qute.fmt(
                    """
                            Event: {p.event}
                            Area: {p.areaDesc}
                            Severity: {p.severity}
                            Description: {p.description}
                            Instructions: {p.instruction}
                            """,
                    Map.of("p", feature.properties()));
        }).collect(Collectors.joining("\n---\n"));
    }

    @RegisterRestClient(baseUri = "https://api.weather.gov")
    public interface WeatherClient {
        @GET
        @Path("/alerts/active/area/{state}")
        Alerts getAlerts(@RestPath String state);

        @GET
        @Path("/points/{latitude},{longitude}")
        Map<String, Object> getPoints(@RestPath double latitude, @RestPath double longitude);

        @GET
        @Path("/")
        Forecast getForecast(@Url String url);
    }

    public record Properties(
            String id,
            String areaDesc,
            String event,
            String severity,
            String description,
            String instruction) {
    }

    public record Feature(
            String id,
            String type,
            Object geometry,
            Properties properties) {
    }

    public record Alerts(
            List<String> context,
            String type,
            List<Feature> features,
            String title,
            String updated) {
    }

    public record Period(
            String name,
            int temperature,
            String temperatureUnit,
            String windSpeed,
            String windDirection,
            String detailedForecast) {
    }

    public record ForecastProperties(
            List<Period> periods) {
    }

    public record Forecast(
            ForecastProperties properties) {
    }

}