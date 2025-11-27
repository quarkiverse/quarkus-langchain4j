package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.List;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class ParallelAgents {
    public interface FoodExpert {

        @UserMessage("""
                You are a great evening planner.
                Propose a list of 3 meals matching the given mood.
                The mood is {{mood}}.
                For each meal, just give the name of the meal.
                Provide a list with the 3 items and nothing else.
                """)
        @Agent(outputKey = "meals")
        List<String> findMeal(@V("mood") String mood);
    }

    public interface MovieExpert {

        @UserMessage("""
                You are a great evening planner.
                Propose a list of 3 movies matching the given mood.
                The mood is {{mood}}.
                Provide a list with the 3 items and nothing else.
                """)
        @Agent(outputKey = "movies")
        List<String> findMovie(@V("mood") String mood);
    }

    public record EveningPlan(String movie, String meal) {
    }

    public interface EveningPlannerAgent {

        @ParallelAgent(outputKey = "plans", subAgents = { FoodExpert.class, MovieExpert.class })
        List<EveningPlan> plan(@V("mood") String mood);
    }
}
