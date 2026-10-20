package io.quarkiverse.langchain4j.agentic.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

@SuppressWarnings("CdiInjectionPointsInspection")
public class AgentBeanSmokeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(Agents.class, ParallelAgents.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    Agents.ExpertRouterAgent expertRouterAgent;

    @Inject
    Agents.NoneAiAgent noneAiAgent;

    @Inject
    Agents.SupervisorStoryCreator supervisorStoryCreator;

    @Inject
    ParallelAgents.EveningPlannerAgent eveningPlannerAgent;

    @Inject
    Agents.MedicalExpertWithMemory medicalExpertWithMemory;

    @Inject
    Agents.StoryCreator storyCreator;

    @Test
    public void test() {
        Assertions.assertNotNull(expertRouterAgent);
        Assertions.assertNotNull(noneAiAgent);
        Assertions.assertNotNull(supervisorStoryCreator);
    }
}
