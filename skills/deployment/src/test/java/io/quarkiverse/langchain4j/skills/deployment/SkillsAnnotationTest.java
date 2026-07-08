package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;
import io.quarkiverse.langchain4j.skills.runtime.DefaultSkillsConfigurator;
import io.quarkus.test.QuarkusUnitTest;

public class SkillsAnnotationTest {

    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", SKILLS_DIR.toString());

    @Inject
    SkillsConfigurator skillsConfigurator;

    @Test
    void skillsConfiguratorIsAvailable() {
        assertThat(skillsConfigurator).isNotNull();
        assertThat(skillsConfigurator).isInstanceOf(DefaultSkillsConfigurator.class);
    }

    @Test
    void allSkillsToolProvider() {
        var toolProvider = skillsConfigurator.createToolProvider(List.of());
        assertThat(toolProvider).isNotNull();
        var result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet()).isNotEmpty();
    }

    @Test
    void allSkillsFormatted() {
        String formatted = skillsConfigurator.formatAvailableSkills(List.of());
        assertThat(formatted).contains("foobar-skill");
        assertThat(formatted).contains("bazqux-skill");
    }

    @Test
    void filteredSkillsToolProvider() {
        var toolProvider = skillsConfigurator.createToolProvider(List.of("foobar-skill"));
        assertThat(toolProvider).isNotNull();
    }

    @Test
    void filteredSkillsFormatted() {
        String formatted = skillsConfigurator.formatAvailableSkills(List.of("foobar-skill"));
        assertThat(formatted).contains("foobar-skill");
        assertThat(formatted).doesNotContain("bazqux-skill");
    }

    @Test
    void unknownSkillNameFailsFast() {
        assertThatThrownBy(() -> skillsConfigurator.createToolProvider(List.of("nonexistent")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("Available skills");
    }

    @Test
    void unknownSkillNameInFormattedFailsFast() {
        assertThatThrownBy(() -> skillsConfigurator.formatAvailableSkills(List.of("nonexistent")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }
}
