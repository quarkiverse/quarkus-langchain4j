package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.skills.SkillsSystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The extension registers a {@link SkillsToolProvider} bean unconditionally, so the bean is present
 * even when {@code quarkus.langchain4j.skills.directories} is not set - but in that case it carries
 * no {@code Skills} instance at all. Anything that reads the provider has to cope with that.
 */
public class SkillsWithoutDirectoriesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    SkillsToolProvider toolProvider;

    @Test
    void theProviderIsRegisteredButCarriesNoSkills() {
        assertThat(toolProvider).isNotNull();
        assertThat(toolProvider.getSkills()).isNull();
    }

    @Test
    void systemMessageProviderToleratesTheMissingConfiguration() {
        Optional<String> systemMessage = new SkillsSystemMessageProvider().getSystemMessage(null);
        assertThat(systemMessage).isEmpty();
    }
}
