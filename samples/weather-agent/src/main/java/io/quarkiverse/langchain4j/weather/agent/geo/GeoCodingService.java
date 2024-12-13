package io.quarkiverse.langchain4j.weather.agent.geo;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.cache.CacheResult;
import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

@RegisterRestClient(configKey = "geocoding")
@Path("/v1")
public interface GeoCodingService {

    @GET
    @Path("/search")
    @CacheResult(cacheName = "geo-results")
    @ClientQueryParam(name = "count", value = "1")
    @Tool("Finds the latitude and longitude of a given city")
    GeoResults search(@RestQuery String name);

}
