package io.quarkiverse.langchain4j.agentic.deployment.validation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class HumanInTheLoopAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(StoryCreatorWithHumanInTheLoop.class, AudienceRetriever.class, CreativeWriter.class,
                                    HumanResponseSupplier.class, AudienceEditor.class, AudienceReader.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void test() {
        // we don't need to do anything as we just wanted to make sure that validation doesn't fail the build
    }

    @Inject
    StoryCreatorWithHumanInTheLoop storyCreator;

    public interface StoryCreatorWithHumanInTheLoop {

        @SequenceAgent(outputKey = "story", subAgents = {
                AudienceRetriever.class,
                CreativeWriter.class,
                HumanResponseSupplier.class,
                AudienceEditor.class,
                AudienceReader.class
        })
        String write(@V("topic") String topic);
    }

    private static final AtomicReference<String> requestRef = new AtomicReference<>();
    private static final AtomicReference<String> audienceRef = new AtomicReference<>();

    public interface AudienceRetriever {

        @HumanInTheLoop(description = "Generate a story based on the given topic", outputKey = "audience", async = true)
        static String humanResponse(AgenticScope scope, @V("topic") String topic) {
            requestRef.set("Which audience for topic " + topic + "?");
            CompletableFuture<String> futureResult = new CompletableFuture<>();
            HumanResponseSupplier.pendingResponses.put(scope.memoryId(), futureResult);
            try {
                String result = futureResult.get();
                HumanResponseSupplier.pendingResponses.remove(scope.memoryId());
                return result;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class HumanResponseSupplier {

        static final Map<Object, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

        @Agent
        public static void await(AgenticScope scope) {
            pendingResponses.get(scope.memoryId()).complete("young adults");
        }
    }

    public static class AudienceReader {

        @Agent
        public static void readAudience(@V("audience") String audience) {
            audienceRef.set(audience);
        }
    }

    public interface CreativeWriter {

        @UserMessage("""
                You are a creative writer.
                Generate a draft of a story long no more than 3 sentence around the given topic.
                Return only the story and nothing else.
                The topic is {{topic}}.
                """)
        @Agent(description = "Generate a story based on the given topic", outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    public interface AudienceEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better align with the target audience of {{audience}}.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story") String story, @V("audience") String audience);
    }
}
