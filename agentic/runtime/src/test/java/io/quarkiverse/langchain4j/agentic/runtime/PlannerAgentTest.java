package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.supervisor.PlannerAgent;
import dev.langchain4j.service.SystemMessage;

/**
 * This class only exists so we make sure we catch changes made to {@link dev.langchain4j.agentic.supervisor.PlannerAgent}
 * and update {@link Constants#PLANNER_AGENT_CONTENT_TO_REPLACE_IN_QUTE} when we bump LangChain4j version.
 */
class PlannerAgentTest {

    @Test
    void testNothingChanged() {
        List<Method> planMethods = Arrays.stream(PlannerAgent.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("plan"))
                .toList();
        Assertions.assertEquals(1, planMethods.size(),
                "The '" + PlannerAgent.class.getName() + "' class was expected to have a single 'plan' method");
        Method planMethod = planMethods.get(0);
        SystemMessage systemMessage = planMethod.getAnnotation(SystemMessage.class);
        Assertions.assertNotNull(systemMessage,
                "The 'plan' method of '" + PlannerAgent.class.getName()
                        + "' class was expected to be annotated with @SystemMessage");
        String[] systemMessageValues = systemMessage.value();
        Assertions.assertEquals(1, systemMessageValues.length, "The @SystemMessage annotation of the 'plan' method of '"
                + PlannerAgent.class.getName() + "' class was expected to have a single value");
        Assertions.assertTrue(systemMessageValues[0].contains(Constants.PLANNER_AGENT_CONTENT_TO_REPLACE_IN_QUTE),
                "The @SystemMessage annotation of the 'plan' method of '" + PlannerAgent.class.getName()
                        + "' class has changed\"");
    }

}
