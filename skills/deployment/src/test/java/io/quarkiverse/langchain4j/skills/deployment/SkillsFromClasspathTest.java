package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Test for loading skills from classpath resources.
 * The quarkus.langchain4j.skills.directories property is prepended with "classpath:" in this case.
 */
public class SkillsFromClasspathTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("skills/foobar-skill/SKILL.md", "skills/foobar-skill/SKILL.md")
                    .addAsResource("skills/foobar-skill/references/guide.md", "skills/foobar-skill/references/guide.md"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", "classpath:skills");

    @Inject
    SkillsToolProvider toolProvider;

    @Test
    void toolProviderIsRegistered() {
        assertThat(toolProvider).isNotNull();
    }

    @Test
    void activateSkillReturnsSkillContent() {
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

    @Test
    void readSkillResourceReturnsResourceContent() {
        ToolProviderResult result = toolProvider.provideTools(null);

        ToolExecutor readResource = result.toolExecutorByName("read_skill_resource");
        assertThat(readResource).isNotNull();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("read_skill_resource")
                .arguments("{\"skill_name\": \"foobar-skill\", \"relative_path\": \"references/guide.md\"}")
                .build();

        ToolExecutionResult resourceResult = readResource.executeWithContext(request, null);
        assertThat(resourceResult.resultText()).contains("This is the foobar guide resource content.");
    }
}
