package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService( )
public interface AiWeatherService {

    @SystemMessage("You are a weather expert")
    @UserMessage("""
                Get the most recent weather alerts for a given state with state code {state}
            """)
    String getWeatherAlerts(String state);

    @SystemMessage("""
            You are a weather expert. The user will give you a location, and you should first
            get the coordinates for that location, and then based on the coordinates,
            get the weather for that specific location.
            If you can get the US State, then you should also return any weather alerts for the location.
            """)
    String getWeather(String message);
}
