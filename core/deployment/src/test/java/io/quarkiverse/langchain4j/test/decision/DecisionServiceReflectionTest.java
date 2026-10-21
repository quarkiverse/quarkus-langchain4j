package io.quarkiverse.langchain4j.test.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.Scale;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The types that upstream reads by reflection to implement a Decision Service are registered for the native image. The
 * registrations are checked by a build step, which fails the build when one is missing.
 */
public class DecisionServiceReflectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .addBuildChainCustomizer(checkRegistrations());

    public enum Team {
        BILLING,
        SUPPORT
    }

    public enum Severity {
        LOW,
        HIGH
    }

    public record Ticket(String subject, String body) {
    }

    public record Triage(
            @Decide("Which team should handle this ticket?") Choice<Team> team,
            @Decide("How severe is this ticket?") Scale<Severity> severity) {
    }

    public interface Triager {
        Triage triage(Ticket ticket);
    }

    @RegisterDecisionService
    public interface TicketTriage extends Triager {
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed");
        }
    }

    private static Consumer<BuildChainBuilder> checkRegistrations() {
        return builder -> builder.addBuildStep(new BuildStep() {
            @Override
            public void execute(BuildContext context) {
                List<ReflectiveClassBuildItem> items = context.consumeMulti(ReflectiveClassBuildItem.class);
                assertRegistered(items, TicketTriage.class, false);
                assertRegistered(items, Triager.class, false);
                assertRegistered(items, Triage.class, true);
                assertRegistered(items, Team.class, true);
                assertRegistered(items, Severity.class, true);
                assertRegistered(items, Ticket.class, true);
                context.produce(new FeatureBuildItem("decision-reflection-check"));
            }
        }).consumes(ReflectiveClassBuildItem.class).produces(FeatureBuildItem.class).build();
    }

    private static void assertRegistered(List<ReflectiveClassBuildItem> items, Class<?> type, boolean withFields) {
        assertThat(items)
                .as("reflective registration of %s", type.getName())
                .anySatisfy(item -> {
                    assertThat(item.getClassNames()).contains(type.getName());
                    assertThat(item.isMethods()).isTrue();
                    if (withFields) {
                        assertThat(item.isFields()).isTrue();
                        assertThat(item.isConstructors()).isTrue();
                    }
                });
    }

    @Test
    void registered() {
        // the checks run at build time
    }
}
