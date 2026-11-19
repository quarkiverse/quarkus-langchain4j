package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.langchain4j.agentic.runtime.devui.AgenticJsonRpcService;

@Path("/remote-story")
public class RemoteStoryResource {

    // never invoked: the injection point tells the build that the arguments of the workflow come from its caller
    @Inject
    RemoteStoryWorkflow workflow;

    @GET
    @Path("/topology")
    public String topology() {
        return new AgenticJsonRpcService().getTopologyReportHtml(0);
    }
}
