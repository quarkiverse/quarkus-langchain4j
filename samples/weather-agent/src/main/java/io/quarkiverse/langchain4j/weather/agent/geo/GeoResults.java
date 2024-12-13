package io.quarkiverse.langchain4j.weather.agent.geo;

import java.util.List;

public record GeoResults(List<GeoResult> results) {

    public GeoResult getFirst() {
        return results.get(0);
    }

}
