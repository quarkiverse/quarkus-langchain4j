package io.quarkiverse.langchain4j.test.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionServiceTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class));

    public enum Team {
        BILLING,
        SUPPORT
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public record Triage(
            @Decide("Which team should handle this ticket?") Team team,
            @Decide("Does this ticket need attention today?") boolean urgent) {
    }

    public interface Classifier {
        @Decide("Is this message about billing?")
        boolean billing(String message);
    }

    @RegisterDecisionService
    public interface TicketDesk extends Classifier {

        @Decide("Is this message spam?")
        boolean isSpam(String message);

        @Decide("Which team should handle this request?")
        Team route(String request);

        Triage triage(String ticket);

        @Decide("Which team should handle this request?")
        Choice<Team> choose(String request);

        @Decide("How severe is this incident?")
        Scale<Severity> rate(String incident);

        @Decide("Does this ticket need attention today?")
        YesNoAnswer urgent(String ticket);

        @Decide("Which team should handle this request?")
        DecisionResult<Team> routeWithResult(String request);

        @Decide("Is this message spam?")
        CompletableFuture<Boolean> isSpamAsync(String message);

        @Decide("Is this message spam?")
        boolean isSpamWithParameters(String message, DecisionRequestParameters parameters);

        default String describe(String message) {
            return isSpam(message) ? "spam" : "not spam";
        }
    }

    @RegisterDecisionService(modelName = "m1", scope = Dependent.class)
    public interface NamedModelService {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel defaultModel() {
            return new FixedDecisionModel("default")
                    .yesNo("billing", 0.8)
                    .yesNo("isSpam", 0.9)
                    .choice("route", "SUPPORT", Map.of("BILLING", 0.3, "SUPPORT", 0.7))
                    .choice("team", "BILLING", Map.of("BILLING", 0.8, "SUPPORT", 0.2))
                    .yesNo("urgent", 0.7)
                    .choice("choose", "BILLING", Map.of("BILLING", 0.6, "SUPPORT", 0.4))
                    .scale("rate", 1.4, List.of(0.1, 0.4, 0.5))
                    .choice("routeWithResult", "SUPPORT", Map.of("BILLING", 0.1, "SUPPORT", 0.9))
                    .yesNo("isSpamAsync", 0.9)
                    .yesNo("isSpamWithParameters", 0.9);
        }

        @Produces
        @ApplicationScoped
        @ModelName("m1")
        DecisionModel namedModel() {
            return new FixedDecisionModel("model-m1").yesNo("isSpam", 0.1);
        }
    }

    @Inject
    TicketDesk ticketDesk;

    @Inject
    NamedModelService namedModelService;

    @Inject
    DecisionModel defaultModel;

    @Test
    void implementedByTheGeneratedClass() {
        assertThat(ClientProxy.unwrap(ticketDesk).getClass().getName()).endsWith("$$QuarkusDecisionServiceImpl");
        assertThat(Arc.container().instance(TicketDesk.class).getBean().getScope()).isEqualTo(ApplicationScoped.class);
    }

    @Test
    void booleanAnswer() {
        assertThat(ticketDesk.isSpam("You won a cruise!")).isTrue();
    }

    @Test
    void methodOfSuperinterface() {
        assertThat(ticketDesk.billing("My invoice is wrong")).isTrue();
    }

    @Test
    void defaultMethod() {
        assertThat(ticketDesk.describe("You won a cruise!")).isEqualTo("spam");
    }

    @Test
    void enumAnswer() {
        assertThat(ticketDesk.route("The app crashes")).isEqualTo(Team.SUPPORT);
    }

    @Test
    void recordAnswer() {
        assertThat(ticketDesk.triage("Payouts failing")).isEqualTo(new Triage(Team.BILLING, true));
    }

    @Test
    void choiceAnswer() {
        assertThat(ticketDesk.choose("Refund please").probabilityOf(Team.SUPPORT)).isEqualTo(0.4);
    }

    @Test
    void scaleAnswer() {
        assertThat(ticketDesk.rate("The site is down").mostLikely()).isEqualTo(Severity.HIGH);
    }

    @Test
    void yesNoAnswer() {
        assertThat(ticketDesk.urgent("Payouts failing").probability()).isEqualTo(0.7);
    }

    @Test
    void decisionResultAnswer() {
        DecisionResult<Team> result = ticketDesk.routeWithResult("The app crashes");
        assertThat(result.content()).isEqualTo(Team.SUPPORT);
        assertThat(result.response().modelName()).isEqualTo("default");
    }

    @Test
    void asyncAnswer() {
        assertThat(ticketDesk.isSpamAsync("You won a cruise!").join()).isTrue();
    }

    @Test
    void requestParameters() {
        assertThat(ticketDesk.isSpamWithParameters("Hello",
                DecisionRequestParameters.builder().modelName("another-model").build())).isTrue();

        FixedDecisionModel model = (FixedDecisionModel) ClientProxy.unwrap(defaultModel);
        assertThat(model.requests()).last().satisfies(request -> assertThat(request.modelName())
                .isEqualTo("another-model"));
    }

    @Test
    void namedModelAndScope() {
        assertThat(namedModelService.isSpam("You won a cruise!")).isFalse();
        assertThat(Arc.container().instance(NamedModelService.class).getBean().getScope()).isEqualTo(Dependent.class);
    }
}
