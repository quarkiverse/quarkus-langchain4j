package io.quarkiverse.langchain4j.weather.agent;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.weather.agent.geo.GeoCodingService;
import io.quarkiverse.langchain4j.weather.agent.weather.WeatherForecastService;

@RegisterAiService(tools = { CityExtractorAgent.class, WeatherForecastService.class, GeoCodingService.class})
public interface WeatherForecastAgent {

    @SystemMessage("""
    You are a meteorologist, and you need to answer questions asked by the user about weather using at most 3 lines.
    
    The weather information is a JSON object and has the following fields:
    
    maxTemperature is the maximum temperature of the day in Celsius degrees
    minTemperature is the minimum temperature of the day in Celsius degrees
    precipitation is the amount of water in mm
    windSpeed is the speed of wind in kilometers per hour
    weather is the overall weather.
    """)
    String chat(String query);

}
