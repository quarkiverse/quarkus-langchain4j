package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionAgentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            // the decision model is produced by the test
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .setLogRecordPredicate(DecisionAgentTest::isStandaloneWarning)
            .assertLogRecords(DecisionAgentTest::assertWarnsAboutStandaloneInvocation);

    public enum Team {
        BILLING,
        SUPPORT
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(name = "spamGate", description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    public interface TeamRouter {
        @Decide("Which team should handle this request?")
        @Agent(description = "Routes a request to a team", outputKey = "team")
        Team route(@V("request") String request);
    }

    public record Triage(
            @Decide("Which team should handle this ticket?") Team team,
            @Decide("Does this ticket need attention today?") boolean urgent) {
    }

    /**
     * Neither annotated with {@code @Decide} nor declaring a {@code @DecisionModelSupplier}: a decision agent because of
     * the {@code @Decide} fields of the record it returns.
     */
    public interface TicketTriage {
        @Agent(description = "Triages a ticket", outputKey = "triage")
        Triage triage(@V("ticket") String ticket);
    }

    public interface ChoiceRouter {
        @Decide("Which team should handle this request?")
        @Agent(description = "Chooses a team", outputKey = "teamChoice")
        Choice<Team> choose(@V("request") String request);
    }

    public interface SeverityRater {
        @Decide("How severe is this incident?")
        @Agent(description = "Rates an incident", outputKey = "severity")
        Scale<Severity> rate(@V("incident") String incident);
    }

    public interface UrgencyGate {
        @Decide("Does this ticket need attention today?")
        @Agent(description = "Checks the urgency of a ticket", outputKey = "urgency")
        YesNoAnswer urgent(@V("ticket") String ticket);
    }

    public interface ResultRouter {
        @Decide("Which team should handle this request?")
        @Agent(description = "Routes a request and returns the response", outputKey = "teamResult")
        DecisionResult<Team> routeWithResult(@V("request") String request);
    }

    public static class Responder {
        @Agent(description = "Replies to a message", outputKey = "reply")
        public static String reply(@V("message") String message, @V("spam") boolean spam) {
            return spam ? "rejected" : "accepted: " + message;
        }
    }

    public interface SpamSequence {
        @SequenceAgent(outputKey = "reply", subAgents = { SpamGate.class, Responder.class })
        String handle(@V("message") String message);
    }

    public static class BillingDesk {
        @Agent(description = "Answers billing requests", outputKey = "answer")
        public static String billing(@V("request") String request) {
            return "billing: " + request;
        }
    }

    public static class SupportDesk {
        @Agent(description = "Answers support requests", outputKey = "answer")
        public static String support(@V("request") String request) {
            return "support: " + request;
        }
    }

    public interface Desks {
        @ConditionalAgent(outputKey = "answer", subAgents = { BillingDesk.class, SupportDesk.class })
        String answer(@V("request") String request);

        @ActivationCondition(BillingDesk.class)
        static boolean billing(@V("team") Team team) {
            return team == Team.BILLING;
        }

        @ActivationCondition(SupportDesk.class)
        static boolean support(@V("team") Team team) {
            return team == Team.SUPPORT;
        }
    }

    public interface HelpDesk {
        @SequenceAgent(outputKey = "answer", subAgents = { TeamRouter.class, Desks.class })
        String handle(@V("request") String request);
    }

    public static class Counter {
        @Agent(description = "Counts", outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    public interface DoneGate {
        @Decide("Is the work done?")
        @Agent(description = "Checks whether the work is done", outputKey = "done")
        boolean done(@V("count") int count);
    }

    public interface WorkLoop {
        @LoopAgent(outputKey = "count", maxIterations = 5, subAgents = { Counter.class, DoneGate.class })
        int work(@V("count") int count);

        @ExitCondition(testExitAtLoopEnd = true)
        static boolean exit(@V("done") boolean done) {
            return done;
        }
    }

    @ApplicationScoped
    public static class DecisionListener implements AgentListener {

        private final List<AgentResponse> responses = new CopyOnWriteArrayList<>();

        List<AgentResponse> responses() {
            return responses;
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            if (agentResponse.decisionResponse() != null) {
                responses.add(agentResponse);
            }
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed-model")
                    .yesNo("isSpam", 0.9)
                    .choice("route", "SUPPORT", Map.of("BILLING", 0.3, "SUPPORT", 0.7))
                    .choice("team", "BILLING", Map.of("BILLING", 0.8, "SUPPORT", 0.2))
                    .yesNo("urgent", 0.7)
                    .choice("choose", "BILLING", Map.of("BILLING", 0.6, "SUPPORT", 0.4))
                    .scale("rate", 1.4, List.of(0.1, 0.4, 0.5))
                    .choice("routeWithResult", "SUPPORT", Map.of("BILLING", 0.1, "SUPPORT", 0.9))
                    .yesNo("done", 0.9);
        }
    }

    @Inject
    SpamGate spamGate;

    @Inject
    TeamRouter teamRouter;

    @Inject
    TicketTriage ticketTriage;

    @Inject
    ChoiceRouter choiceRouter;

    @Inject
    SeverityRater severityRater;

    @Inject
    UrgencyGate urgencyGate;

    @Inject
    ResultRouter resultRouter;

    @Inject
    SpamSequence spamSequence;

    @Inject
    HelpDesk helpDesk;

    @Inject
    WorkLoop workLoop;

    @Inject
    DecisionListener listener;

    @Test
    void decisionAgentIsImplementedByTheGeneratedDecisionClass() {
        Object agent = ClientProxy.unwrap(spamGate);
        assertThat(agent.getClass().getName()).endsWith("$$QuarkusDecisionAgentImpl");
        assertThat(agent).isNotInstanceOf(ChatMemoryAccess.class).isNotInstanceOf(AiServiceResponseReceivedListener.class);
        assertThat(((AgentInstance) agent).topology()).isEqualTo(AgenticSystemTopology.DECISION_AGENT);
        assertThat(((AgentInstance) agent).name()).isEqualTo("spamGate");
    }

    @Test
    void booleanAnswer() {
        assertThat(spamGate.isSpam("You won a cruise!")).isTrue();
    }

    @Test
    void enumAnswer() {
        assertThat(teamRouter.route("My card was charged twice")).isEqualTo(Team.SUPPORT);
    }

    @Test
    void recordAnswerWithoutDecideOnTheMethod() {
        assertThat(ticketTriage.triage("Payouts failing for 3 days")).isEqualTo(new Triage(Team.BILLING, true));
    }

    @Test
    void choiceAnswer() {
        Choice<Team> choice = choiceRouter.choose("Refund please");
        assertThat(choice.value()).isEqualTo(Team.BILLING);
        assertThat(choice.probabilityOf(Team.SUPPORT)).isEqualTo(0.4);
    }

    @Test
    void scaleAnswer() {
        Scale<Severity> scale = severityRater.rate("The site is down");
        assertThat(scale.mostLikely()).isEqualTo(Severity.HIGH);
        assertThat(scale.mean()).isEqualTo(1.4);
    }

    @Test
    void yesNoAnswer() {
        assertThat(urgencyGate.urgent("Payouts failing").probability()).isEqualTo(0.7);
    }

    @Test
    void decisionResultAnswer() {
        DecisionResult<Team> result = resultRouter.routeWithResult("The app crashes");
        assertThat(result.content()).isEqualTo(Team.SUPPORT);
        assertThat(result.response().modelName()).isEqualTo("fixed-model");
    }

    @Test
    void decisionAgentInsideSequence() {
        assertThat(spamSequence.handle("You won a cruise!")).isEqualTo("rejected");
    }

    @Test
    void decisionAgentRoutingConditionalAgent() {
        assertThat(helpDesk.handle("The app crashes")).isEqualTo("support: The app crashes");
    }

    @Test
    void decisionAgentEndingLoop() {
        assertThat(workLoop.work(0)).isEqualTo(1);
    }

    @Test
    void listenerReceivesTheDecisionResponse() {
        listener.responses().clear();
        spamSequence.handle("You won a cruise!");

        assertThat(listener.responses()).singleElement().satisfies(response -> {
            assertThat(response.agentName()).isEqualTo("spamGate");
            assertThat(response.output()).isEqualTo(true);
            DecisionResponse decisionResponse = response.decisionResponse();
            assertThat(decisionResponse.yesNo("isSpam").probability()).isEqualTo(0.9);
            assertThat(decisionResponse.modelName()).isEqualTo("fixed-model");
        });
    }

    private static boolean isStandaloneWarning(LogRecord record) {
        return record.getLevel().intValue() >= Level.WARNING.intValue()
                && record.getMessage() != null
                && record.getMessage().contains("standalone decision agent");
    }

    private static void assertWarnsAboutStandaloneInvocation(List<LogRecord> records) {
        // the direct invocations of the decision agents warn, those inside agentic systems do not
        Assertions.assertThat(records).isNotEmpty();
    }
}
