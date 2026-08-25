package io.quarkiverse.langchain4j.sample.a2a;

import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * A support ticket router that classifies requests and delegates to
 * specialized agents discovered from Apicurio Registry.
 *
 * The classifier is a local AI service. The specialist agents
 * (billing, technical, shipping) are loaded from the registry
 * via {@code @RegistryAgent} and wired into a sequence.
 */
public class SupportRouter {

    @RegisterAiService
    public interface TicketClassifier {

        @SystemMessage("""
                You are a support ticket classifier.
                Analyze the customer request and determine which department should handle it.
                Reply with exactly one of: billing, technical, shipping.
                """)
        @UserMessage("Classify this request: {{request}}")
        String classify(@V("request") String request);
    }

    public interface BillingAgent {
        @RegistryAgent("billing-agent")
        String handleBillingIssue(@V("request") String request);
    }

    public interface TechnicalAgent {
        @RegistryAgent("technical-agent")
        String handleTechnicalIssue(@V("request") String request);
    }

    public interface ShippingAgent {
        @RegistryAgent("shipping-agent")
        String handleShippingIssue(@V("request") String request);
    }
}
