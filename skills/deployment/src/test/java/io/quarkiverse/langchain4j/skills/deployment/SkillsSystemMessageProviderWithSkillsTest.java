package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.skills.SkillsSystemMessageProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The populated case has to keep working: a configured directory that holds skills must still
 * produce a system message. The value is written with forward slashes so this test does not depend
 * on the platform separator surviving the trip through the config layer.
 */
public class SkillsSystemMessageProviderWithSkillsTest {

    private static final String SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath().toString()
            .replace('\\', '/');

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", SKILLS_DIR);

    @Test
    void systemMessageListsTheConfiguredSkills() {
        Optional<String> systemMessage = new SkillsSystemMessageProvider().getSystemMessage(null);
        assertThat(systemMessage).isPresent();
        assertThat(systemMessage.get()).contains("You have access to the following skills");
        assertThat(systemMessage.get()).contains("foobar-skill");
    }
}
