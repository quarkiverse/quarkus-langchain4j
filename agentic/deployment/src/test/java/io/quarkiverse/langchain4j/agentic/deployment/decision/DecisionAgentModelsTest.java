package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionAgentModelsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FixedDecisionModel.class, Agents.FixedResponseChatModel.class))
            // the models are produced by the test
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    public interface DefaultModelGate {
        @Decide("Is this message spam?")
        @Agent(description = "Uses the default decision model", outputKey = "spam")
        boolean check(@V("message") String message);
    }

    public interface NamedModelGate {
        @Decide("Is this message spam?")
        @Agent(description = "Uses a named decision model", outputKey = "spam")
        @ModelName("m1")
        boolean check(@V("message") String message);
    }

    public interface SuppliedModelGate {
        @Agent(description = "Uses the decision model of its supplier", outputKey = "spam")
        @Decide("Is this message spam?")
        boolean check(@V("message") String message);

        @DecisionModelSupplier
        static DecisionModel decisionModel() {
            return new FixedDecisionModel("supplied").yesNo("check", 0.8);
        }
    }

    public interface CdiSuppliedModelGate {
        @Agent(description = "Uses the decision model of a supplier taking a CDI bean", outputKey = "spam")
        @Decide("Is this message spam?")
        boolean check(@V("message") String message);

        @DecisionModelSupplier
        static DecisionModel decisionModel(@CdiBean ModelFactory factory) {
            return factory.decisionModel();
        }
    }

    @ApplicationScoped
    public static class ModelFactory {
        DecisionModel decisionModel() {
            return new FixedDecisionModel("from-cdi").yesNo("check", 0.95);
        }
    }

    public interface Writer {
        @UserMessage("Write a story about {{topic}}")
        @Agent(description = "Writes a story", outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface StoryGate {
        @Decide("Is this story appropriate for children?")
        @Agent(description = "Checks a story", outputKey = "appropriate")
        boolean appropriate(@V("story") String story);
    }

    public interface StoryPipeline {
        @SequenceAgent(outputKey = "appropriate", subAgents = { Writer.class, StoryGate.class })
        boolean create(@V("topic") String topic);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel defaultModel() {
            return new FixedDecisionModel("default").yesNo("check", 0.1).yesNo("appropriate", 0.9);
        }

        @Produces
        @ApplicationScoped
        @ModelName("m1")
        DecisionModel namedModel() {
            return new FixedDecisionModel("model-m1").yesNo("check", 0.9);
        }

        @Produces
        @ApplicationScoped
        ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("a story about dragons");
        }
    }

    @Inject
    DefaultModelGate defaultModelGate;

    @Inject
    NamedModelGate namedModelGate;

    @Inject
    SuppliedModelGate suppliedModelGate;

    @Inject
    CdiSuppliedModelGate cdiSuppliedModelGate;

    @Inject
    StoryPipeline storyPipeline;

    @Inject
    DecisionModel defaultModel;

    @Test
    void defaultDecisionModelBean() {
        assertThat(defaultModelGate.check("Hello")).isFalse();
    }

    @Test
    void namedDecisionModelBean() {
        assertThat(namedModelGate.check("Hello")).isTrue();
    }

    @Test
    void decisionModelSupplier() {
        assertThat(suppliedModelGate.check("Hello")).isTrue();
    }

    @Test
    void decisionModelSupplierWithCdiBean() {
        assertThat(cdiSuppliedModelGate.check("Hello")).isTrue();
    }

    @Test
    void decisionAgentAfterAnAiAgent() {
        assertThat(storyPipeline.create("dragons")).isTrue();

        // the decision agent received the output of the AI agent
        FixedDecisionModel model = (FixedDecisionModel) ClientProxy.unwrap(defaultModel);
        assertThat(model.requests()).last().satisfies(request -> assertThat(((FieldsInput) request.input()).fields())
                .containsEntry("story", "a story about dragons"));
    }
}
