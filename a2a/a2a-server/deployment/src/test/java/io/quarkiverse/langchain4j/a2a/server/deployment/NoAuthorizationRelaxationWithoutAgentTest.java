package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Merely having this extension on the classpath must not relax the SDK's fail-closed task authorization. An
 * application that exposes no agent gets no default from here, so whatever the SDK decides stands.
 */
public class NoAuthorizationRelaxationWithoutAgentTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withEmptyApplication();

    @Test
    public void testTheExtensionContributesNoDefault() {
        assertThat(ConfigProvider.getConfig().getOptionalValue("a2a.authorization.required", Boolean.class))
                .isNotEqualTo(Optional.of(false));
    }
}
