package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonExecutorReturnTypeParallelExecutorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, EveningPlannerAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("Executor"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputName = "plans", subAgents = {
                @SubAgent(type = Agents.FoodExpert.class, outputName = "meals"),
                @SubAgent(type = Agents.MovieExpert.class, outputName = "movies")
        })
        List<Agents.EveningPlan> plan(@V("mood") String mood);

        @ParallelExecutor
        static Object executor() {
            return Executors.newFixedThreadPool(2);
        }

        @Output
        static List<Agents.EveningPlan> createPlans(@V("movies") List<String> movies, @V("meals") List<String> meals) {
            List<Agents.EveningPlan> moviesAndMeals = new ArrayList<>();
            for (int i = 0; i < movies.size(); i++) {
                if (i >= meals.size()) {
                    break;
                }
                moviesAndMeals.add(new Agents.EveningPlan(movies.get(i), meals.get(i)));
            }
            return moviesAndMeals;
        }
    }
}
