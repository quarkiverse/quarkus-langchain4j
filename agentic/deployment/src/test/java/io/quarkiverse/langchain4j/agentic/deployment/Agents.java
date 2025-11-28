package io.quarkiverse.langchain4j.agentic.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class Agents {

    public static class DummyChatModel implements ChatModel {

        private final String response;

        public DummyChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    public interface ExpertRouterAgent {

        @Agent
        String ask(@V("request") String request);
    }

    public interface ExpertRouterAgentWithMemory extends AgenticScopeAccess {

        @Agent
        String ask(@MemoryId String memoryId, @V("request") String request);
    }

    public interface CategoryRouter {

        @UserMessage("""
                Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
                In case the request doesn't belong to any of those categories categorize it as 'unknown'.
                Reply with only one of those words and nothing else.
                The user request is: '{{request}}'.
                """)
        @Agent(description = "Categorize a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("MEDICAL");
        }
    }

    public enum RequestCategory {
        LEGAL,
        MEDICAL,
        TECHNICAL,
        UNKNOWN
    }

    public interface RouterAgent {

        @UserMessage("""
                Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
                then forward the request as it is to the corresponding expert provided as a tool.
                Finally return the answer that you received from the expert without any modification.

                The user request is: '{{it}}'.
                """)
        @Agent
        String askToExpert(String request);
    }

    public interface MedicalExpert {

        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A medical expert")
        @Agent(description = "A medical expert", outputKey = "response")
        String medical(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("\"I'm sorry to hear that you've broken your leg." +
                    "Here are the steps you should take: **Seek Medical Attention");
        }
    }

    public interface MedicalExpertWithMemory {

        @UserMessage("""
                You are a medical expert.
                Analyze the following user request under a medical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A medical expert")
        @Agent(description = "A medical expert", outputKey = "response")
        String medical(@MemoryId String memoryId, @V("request") String request);
    }

    public interface LegalExpert {

        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A legal expert")
        @Agent(description = "A legal expert", outputKey = "response")
        String legal(@V("request") String request);
    }

    public interface LegalExpertWithMemory {

        @UserMessage("""
                You are a legal expert.
                Analyze the following user request under a legal point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A legal expert")
        @Agent(description = "A legal expert", outputKey = "response")
        String legal(@MemoryId String memoryId, @V("request") String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A technical expert")
        @Agent(description = "A technical expert", outputKey = "response")
        String technical(@V("request") String request);
    }

    public interface TechnicalExpertWithMemory {

        @UserMessage("""
                You are a technical expert.
                Analyze the following user request under a technical point of view and provide the best possible answer.
                The user request is {{request}}.
                """)
        @Tool("A technical expert")
        @Agent(description = "A technical expert", outputKey = "response")
        String technical(@MemoryId String memoryId, @V("request") String request);
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

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("""
                    In a realm where dragons soared through the skies, a young wizard named Elara discovered an ancient
                    spell that could bind their fiery hearts to her will. As she summoned the might of a thousand dragons,
                    she realized that true power lay not in control, but in the bond of trust forged between them.
                    Together, they soared into the sunset, guardians of a world where magic and freedom danced in harmony.
                    """);
        }
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

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("""
                    In a world where dragons ruled the skies, a young wizard named Elara stumbled upon a long-lost spell
                    that promised to bend the will of these majestic creatures. Eager to harness their power, she called
                    upon the strength of a thousand dragons. But as she soared through the clouds, she learned a vital
                    lesson: real strength didn’t come from domination, but from the trust and friendship she built with them.
                    United, they flew into the vibrant sunset, protectors of a realm where magic and freedom intertwined,
                    ready to face whatever challenges lay ahead.
                    """);
        }
    }

    public interface StyleEditor {

        @UserMessage("""
                You are a professional editor.
                Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
                Return only the story and nothing else.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given style", outputKey = "story")
        String editStory(@V("story") String story, @V("style") String style);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("""
                    In a realm where dragons soared majestically through the azure skies, a young wizard named Elara
                    unearthed an ancient incantation, whispered of in forgotten tomes, that promised to command the will
                    of these magnificent beasts. With a heart ablaze with ambition, she invoked the strength of a thousand
                    dragons, her spirit entwined with the magic of the spell. As she ascended into the heavens, the winds
                    howled around her, and the clouds danced beneath her feet. Yet, amidst the thrill of her newfound power,
                    Elara discovered a profound truth: true strength was not born of subjugation, but rather from the bonds
                    of trust and kinship forged in the fires of understanding. In that moment of revelation, she cast
                    aside her desire for dominion and reached out to the dragons with an open heart. One by one, they
                    answered her call, their scales shimmering like jewels in the fading light. Together, they soared into
                    the vibrant embrace of the sunset, a tapestry of colors painting the sky, united as guardians of a realm
                    where magic and freedom wove their destinies together. With the winds at their backs and the promise
                    of adventure ahead, Elara and her dragon allies prepared to face the trials that awaited them, champions
                    of a world where friendship reigned supreme.
                    """);
        }
    }

    public interface StyleScorer {

        @UserMessage("""
                You are a critical reviewer.
                Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
                Return only the score and nothing else.

                The story is: "{{story}}"
                """)
        @Agent(description = "Score a story based on how well it aligns with a given style", outputKey = "score")
        double scoreStyle(@V("story") String story, @V("style") String style);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new DummyChatModel("0.85");
        }
    }

    public interface StyleReviewLoop {

        @Agent("Review the given story to ensure it aligns with the specified style")
        String scoreAndReview(@V("story") String story, @V("style") String style);
    }

    public interface StyledWriter extends AgenticScopeAccess {

        @Agent
        ResultWithAgenticScope<String> writeStoryWithStyle(@V("topic") String topic, @V("style") String style);
    }

    @ApplicationScoped
    public static class NoneAiAgent {

        @Inject
        Logger logger;

        @Agent(value = "Produces a Goodbye", outputKey = "goodbye")
        public String goodBye() {
            logger.info("Good Bye");

            return "Good Bye";
        }

    }

    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = { CreativeWriter.class, AudienceEditor.class, StyleEditor.class })
        String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
    }

    public interface StyleReviewLoopAgent {

        @LoopAgent(description = "Review the given story to ensure it aligns with the specified style", outputKey = "story", maxIterations = 5, subAgents = {
                StyleScorer.class, StyleEditor.class })
        String write(@V("story") String story);

        @ExitCondition
        static boolean exit(@V("score") double score) {
            return score >= 0.8;
        }
    }

    public interface SupervisorStoryCreator {

        @SupervisorAgent(outputKey = "story", responseStrategy = SupervisorResponseStrategy.LAST, subAgents = {
                CreativeWriter.class, StyleReviewLoopAgent.class })
        ResultWithAgenticScope<String> write(@V("topic") String topic, @V("style") String style);

        @SupervisorRequest
        static String request(@V("topic") String topic, @V("style") String style) {
            return "Write a story about " + topic + " in " + style + " style";
        }
    }
}
