package io.quarkiverse.langchain4j.weather.agent.weather;

import java.util.Arrays;

public record Daily(double[] temperature_2m_max,
                    double[] temperature_2m_min,
                    double[] precipitation_sum,
                    double[] wind_speed_10m_max,
                    int[] weather_code) {

    public DailyWeatherData getFirstDay() {
        return new DailyWeatherData(temperature_2m_max[0],
            temperature_2m_min[0],
            precipitation_sum[0],
            wind_speed_10m_max[0],
            weather_code[0]);
    }

    @Override
    public String toString() {
        return "Daily{" + "temperature_2m_max=" + Arrays.toString(temperature_2m_max)
               + ", temperature_2m_min=" + Arrays.toString(temperature_2m_min)
               + ", precipitation_sum=" + Arrays.toString(precipitation_sum)
               + ", wind_speed_10m_max=" + Arrays.toString(wind_speed_10m_max)
               + ", weather_code=" + Arrays.toString(weather_code)
               + '}';
    }

}
