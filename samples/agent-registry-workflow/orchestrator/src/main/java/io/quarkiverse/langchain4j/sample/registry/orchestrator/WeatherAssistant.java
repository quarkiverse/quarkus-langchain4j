package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = DiscoveredWeather.class)
public interface WeatherAssistant {
    @SystemMessage("""
            Answer weather questions by calling weatherForCity with the requested city.
            Use only the returned data, explain that it is fictional, and then stop.
            If the tool reports an error, report that error rather than inventing a forecast.
            """)
    String chat(String question);
}
