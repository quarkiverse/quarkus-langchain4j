package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.agentic.declarative.DeclarativeUtil;

@Path("/loop")
public class LoopResource {

    @Inject
    CountingLoopAgent loop;

    @GET
    public int count() {
        return loop.count(0);
    }

    @GET
    @Path("/resolvers")
    public int resolvers() {
        return DeclarativeUtil.getSupplierParameterResolvers().size();
    }
}
