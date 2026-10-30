package io.quarkiverse.langchain4j.agentic.devmode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;

public interface SpamWorkflow extends MonitoredAgent {

    @SequenceAgent(outputKey = "reply", subAgents = { SpamGate.class, Responder.class })
    String handle(@V("message") String message);

    interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(name = "spamGate", description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    class Responder {
        @Agent(description = "Replies to a message", outputKey = "reply")
        public static String reply(@V("spam") boolean spam) {
            return spam ? "rejected" : "accepted";
        }
    }
}
