package io.quarkiverse.langchain4j.agentic.devmode;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;

public interface PricingWorkflow {

    @SequenceAgent(outputKey = "price", subAgents = PricingAgent.class)
    String price(@V("car") String car);

    interface PricingAgent {
        @A2AClientAgent(description = "Prices a car remotely", outputKey = "price")
        String price(@V("car") String car);

        @A2AServerUrlSupplier
        static String a2aServerUrl(@ConfigProperty(name = "pricing-agent.url") String url) {
            return url;
        }
    }
}
