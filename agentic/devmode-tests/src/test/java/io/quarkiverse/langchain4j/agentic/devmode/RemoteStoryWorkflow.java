package io.quarkiverse.langchain4j.agentic.devmode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.service.V;

public interface RemoteStoryWorkflow extends MonitoredAgent {

    @SequenceAgent(outputKey = "story", subAgents = { RemoteWriter.class, Publisher.class })
    String write(@V("topic") String topic);

    interface RemoteWriter {
        // without a description of its own, the agent has the one of its agent card, and no A2A server listens there
        @A2AClientAgent(a2aServerUrl = "http://localhost:1", outputKey = "story")
        String writeStory(@V("topic") String topic);
    }

    class Publisher {
        @Agent(description = "Publishes a story", outputKey = "story")
        public static String publish(@V("story") String story) {
            return story;
        }
    }
}
