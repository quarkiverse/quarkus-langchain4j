package io.quarkiverse.langchain4j.weather.agent;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestQuery;


@Path("/weather")
public class WeatherResource {

    private final WeatherForecastAgent agent;

    public WeatherResource(WeatherForecastAgent agent) {
        this.agent = agent;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getWeather(@RestQuery @DefaultValue("Manilla") String city) {
        return agent.chat(String.format("What is the weather in %s ?", city));
    }


}
