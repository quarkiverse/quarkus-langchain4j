package io.quarkiverse.langchain4j.agentic.devmode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.arc.Unremovable;

/**
 * Agentic systems of the Dev UI test application: a story workflow using every workflow type, two systems sharing a
 * sub-agent, and a {@link MonitoredAgent} root carrying its own monitor.
 */
public class AgenticDevUiApp {

    @Singleton
    public static class WordCounter {

        @Tool("Counts the words of a text")
        public int countWords(String text) {
            return text.split("\\s+").length;
        }
    }

    @Path("/monitor")
    public static class MonitorResource {

        @Inject
        StoryWorkflow storyWorkflow;

        @Inject
        SupportDesk supportDesk;

        @Inject
        HelpDesk helpDesk;

        @Inject
        MonitoredDesk monitoredDesk;

        @GET
        @Path("/own-monitor-sessions")
        public String ownMonitorSessions() {
            return String.valueOf(monitoredDesk.agentMonitor().allMemoryIds());
        }
    }

    public interface StoryWorkflow {

        @SequenceAgent(description = "Writes, reviews and publishes a story", outputKey = "story", subAgents = {
                CreativeWriter.class, StyleReviewLoop.class, ParallelReviews.class, PublishRouter.class })
        String write(@MemoryId String session, @V("topic") String topic, @V("style") String style,
                @V("audience") String audience);
    }

    public interface SupportDesk {

        @SequenceAgent(description = "Answers a support request", outputKey = "echo", subAgents = SharedEcho.class)
        String ask(@MemoryId String session, @V("topic") String topic);
    }

    public interface HelpDesk {

        @SequenceAgent(description = "Answers a help request", outputKey = "echo", subAgents = SharedEcho.class)
        String help(@MemoryId String session, @V("topic") String topic);
    }

    public interface SharedEcho {

        @UserMessage("{{topic}}")
        @Agent(description = "Echoes the topic, shared by two agentic systems", outputKey = "echo")
        String echo(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("echo");
        }
    }

    public interface MonitoredDesk extends MonitoredAgent {

        @SequenceAgent(description = "Answers with its own AgentMonitor", outputKey = "greeting", subAgents = Greeter.class)
        String greet(@MemoryId String session, @V("topic") String topic);
    }

    public interface Greeter {

        @UserMessage("{{topic}}")
        @Agent(description = "Greets", outputKey = "greeting")
        String sayHello(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("greeter");
        }
    }

    public interface CreativeWriter {

        @UserMessage("{{topic}}")
        @Agent(description = "Generate a story based on the given topic", outputKey = "story")
        @ToolBox(WordCounter.class)
        String generateStory(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("writer");
        }
    }

    public interface StyleReviewLoop {

        @LoopAgent(description = "Review the story until it matches the style", outputKey = "story", maxIterations = 3, subAgents = {
                StyleScorer.class, StyleEditor.class })
        String review(@V("story") String story);

        @ExitCondition(description = "score >= 0.8", testExitAtLoopEnd = true)
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    public interface StyleScorer {

        @UserMessage("Score {{story}} for {{style}}")
        @Agent(description = "Score a story against a style", outputKey = "score")
        double scoreStyle(@V("story") String story, @V("style") String style);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("scorer");
        }
    }

    public interface StyleEditor {

        @UserMessage("{{story}}")
        @Agent(description = "Edit a story to better fit a given style", outputKey = "story")
        String editStory(@V("story") String story, @V("style") String style);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("editor");
        }
    }

    public interface ParallelReviews {

        @ParallelAgent(description = "Review the story from several angles", outputKey = "reviews", subAgents = {
                AudienceReviewer.class, LengthChecker.class })
        String reviewAll(@V("story") String story);
    }

    public interface AudienceReviewer {

        @UserMessage("Review {{story}} for {{audience}}")
        @Agent(description = "Check the story fits the audience", outputKey = "audienceReview", async = true)
        String reviewAudience(@V("story") String story, @V("audience") String audience);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("audience");
        }
    }

    @ApplicationScoped
    @Unremovable
    public static class LengthChecker {

        @Agent(description = "Count the words of the story", outputKey = "wordCount")
        public int checkLength(@V("story") String story) {
            return story.split("\\s+").length;
        }
    }

    public interface PublishRouter {

        @ConditionalAgent(description = "Publish on the right channel", outputKey = "published", subAgents = {
                BlogPublisher.class, NewsletterPublisher.class })
        String publish(@V("story") String story);

        @ActivationCondition(value = BlogPublisher.class, description = "wordCount > 10")
        static boolean blog(@V("wordCount") int wordCount) {
            return wordCount > 10;
        }

        @ActivationCondition(value = NewsletterPublisher.class, description = "wordCount <= 10")
        static boolean newsletter(@V("wordCount") int wordCount) {
            return wordCount <= 10;
        }
    }

    public interface BlogPublisher {

        @UserMessage("Publish {{story}}")
        @Agent(description = "Publish the story on the blog", outputKey = "published")
        String publishBlog(@V("story") String story);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("blog");
        }
    }

    public interface NewsletterPublisher {

        @UserMessage("Send {{story}}")
        @Agent(description = "Send the story in the newsletter", outputKey = "published")
        String publishNewsletter(@V("story") String story);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ScriptedChatModel("newsletter");
        }
    }
}
