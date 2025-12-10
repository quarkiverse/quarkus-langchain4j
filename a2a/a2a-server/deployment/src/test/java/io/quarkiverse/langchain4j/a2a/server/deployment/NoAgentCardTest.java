package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCard;
import io.quarkus.test.QuarkusUnitTest;

/**
 * This essentially tests that the A2A library has a producer that throws {@link IllegalStateException}
 */
public class NoAgentCardTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withEmptyApplication();

    @PublicAgentCard
    Instance<AgentCard> agentCard;

    @Test
    public void test() {
        assertThat(agentCard.isResolvable()).isTrue();
        assertThatThrownBy(() -> agentCard.get()).isInstanceOf(IllegalStateException.class);
    }
}
