package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.ParallelAgents;
import io.quarkus.test.QuarkusUnitTest;

public class NonEmptyParametersParallelExecutorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(ParallelAgents.class, EveningPlannerAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("any method parameters"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputKey = "plans", subAgents = { ParallelAgents.FoodExpert.class, ParallelAgents.MovieExpert.class })
        List<ParallelAgents.EveningPlan> plan(@V("mood") String mood);

        @ParallelExecutor
        static Executor executor(Object whatever) {
            return Executors.newFixedThreadPool(2);
        }

        @Output
        static List<ParallelAgents.EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
            List<ParallelAgents.EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new ParallelAgents.EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        }
    }
}
