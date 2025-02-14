package io.quarkiverse.langchain4j.sample.chatbot;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/alerts")
public class MyWeatherResource {

    @Inject
    AiWeatherService aiWeatherService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getWeatherAlertsForUtah(@QueryParam ("state") String state) {
        if (state == null || state.isEmpty()) {
            throw new IllegalArgumentException("State parameter is required");
        }

        String weather = aiWeatherService.getWeatherAlerts(state);
        Log.info(weather);
        return weather;
    }
}
