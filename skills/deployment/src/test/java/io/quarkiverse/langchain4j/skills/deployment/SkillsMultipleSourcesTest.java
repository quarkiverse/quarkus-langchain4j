package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test for loading skills from classpath and filesystem resources at the same time
 */
public class SkillsMultipleSourcesTest {
    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("mixed/boom-skill/SKILL.md", "mixed/boom-skill/SKILL.md"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", "classpath:mixed," + SKILLS_DIR);

    @Inject
    SkillsToolProvider toolProvider;

    @Test
    void toolProviderIsRegistered() {
        assertThat(toolProvider).isNotNull();
    }

    @Test
    void classPathActivationWorks() {
        ToolProviderResult result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        ToolExecutor activateSkill = result.toolExecutorByName("activate_skill");
        assertThat(activateSkill).isNotNull();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("activate_skill")
                .arguments("{\"skill_name\": \"boom-skill\"}")
                .build();

        ToolExecutionResult activationResult = activateSkill.executeWithContext(request, null);
        assertThat(activationResult.resultText()).contains("This is the boom skill content.");
    }

    @Test
    void directoryActivationWorks() {
        ToolProviderResult result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet().stream().map(ToolSpecification::name))
                .containsExactlyInAnyOrder("activate_skill", "read_skill_resource");

        ToolExecutor activateSkill = result.toolExecutorByName("activate_skill");
        assertThat(activateSkill).isNotNull();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("activate_skill")
                .arguments("{\"skill_name\": \"foobar-skill\"}")
                .build();

        ToolExecutionResult activationResult = activateSkill.executeWithContext(request, null);
        assertThat(activationResult.resultText()).contains("This is the foobar skill content.");
    }

}
