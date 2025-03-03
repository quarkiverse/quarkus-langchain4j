package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import dev.langchain4j.Experimental;

@Experimental
public record UtilityAgentToolsRequest(ToolName toolName, UtilityAgentToolInput input, Map<String, Object> config) {
    public sealed interface UtilityAgentToolInput permits StringInput, WebCrawlerInput, WeatherInput {
    }

    public record StringInput(@JsonValue String input) implements UtilityAgentToolInput {
    }

    public record WebCrawlerInput(String url) implements UtilityAgentToolInput {
    }

    public record WeatherInput(String name, String country) implements UtilityAgentToolInput {
    }

    public UtilityAgentToolsRequest(ToolName toolName, UtilityAgentToolInput input) {
        this(toolName, input, null);
    }

    public enum ToolName {

        @JsonProperty("GoogleSearch")
        GOOGLE_SEARCH("GoogleSearch"),

        @JsonProperty("WebCrawler")
        WEB_CRAWLER("WebCrawler"),

        @JsonProperty("Weather")
        WEATHER("Weather"),

        @JsonProperty("PythonInterpreter")
        PYTHON_INTERPRETER("PythonInterpreter");

        private String value;

        ToolName(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }
}
