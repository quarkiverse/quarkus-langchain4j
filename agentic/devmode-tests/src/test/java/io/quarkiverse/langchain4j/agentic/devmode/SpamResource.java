package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.agentic.runtime.devui.AgenticJsonRpcService;

@Path("/spam")
public class SpamResource {

    @Inject
    SpamWorkflow spamWorkflow;

    @GET
    public String handle() {
        return spamWorkflow.handle("You won a cruise!");
    }

    @GET
    @Path("/topology")
    public String topology() {
        return new AgenticJsonRpcService().getTopologyHtml(0);
    }

    @GET
    @Path("/executions")
    public String executions() {
        return new AgenticJsonRpcService().getExecutionReportHtml(0);
    }

    @Produces
    @ApplicationScoped
    DecisionModel decisionModel() {
        return new DecisionModel() {
            @Override
            public DecisionResponse doDecide(DecisionRequest request) {
                return DecisionResponse.builder()
                        .answer("isSpam", YesNoAnswer.from(0.9))
                        .modelName("fixed-model")
                        .tokenUsage(new TokenUsage(10, 1))
                        .build();
            }
        };
    }
}
