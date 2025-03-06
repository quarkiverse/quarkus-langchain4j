package io.quarkiverse.langchain4j.watsonx.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.watsonx.runtime.TextExtraction;
import io.quarkiverse.langchain4j.watsonx.services.GoogleSearchService;
import io.quarkiverse.langchain4j.watsonx.services.WeatherService;
import io.quarkiverse.langchain4j.watsonx.services.WebCrawlerService;

public class WatsonxDotNames {
    public static final DotName WEB_CRAWLER_SERVICE = DotName.createSimple(WebCrawlerService.class);
    public static final DotName GOOGLE_SEARCH_SERVICE = DotName.createSimple(GoogleSearchService.class);
    public static final DotName WEATHER_SERVICE = DotName.createSimple(WeatherService.class);
    public static final DotName TEXT_EXTRACTION = DotName.createSimple(TextExtraction.class);
}
