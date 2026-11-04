package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/loop-bean")
public class LoopBeanResource {

    @Inject
    CountingLoopBean loop;

    @GET
    public int count() {
        return loop.count(0);
    }
}
