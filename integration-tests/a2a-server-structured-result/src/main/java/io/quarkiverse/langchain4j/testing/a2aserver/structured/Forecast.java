package io.quarkiverse.langchain4j.testing.a2aserver.structured;

import java.util.List;

/**
 * The result the agent returns, shaped so that reaching every part of it takes more than knowing this one class:
 * {@link Coordinates} is only reachable through a field, and {@link DailyOutlook} only through a type argument.
 */
public record Forecast(String city, Coordinates at, List<DailyOutlook> days) {

    public record Coordinates(double latitude, double longitude) {
    }

    public record DailyOutlook(String day, int temperature) {
    }
}
