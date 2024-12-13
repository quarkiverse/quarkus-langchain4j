package io.quarkiverse.langchain4j.weather.agent.weather;

import io.vertx.core.json.JsonObject;

public record DailyWeatherData(double temperature_2m_max,
                               double temperature_2m_min,
                               double precipitation_sum,
                               double wind_speed_10m_max,
                               int weather_code) {


    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("maxTemperature", temperature_2m_max());
        json.put("minTemperature", temperature_2m_min());
        json.put("precipitation", precipitation_sum());
        json.put("windSpeed", wind_speed_10m_max());
        json.put("weather", WmoCode.translate(weather_code()));

        return json;
    }

}
