package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.quarkus.test.QuarkusExtensionTest;

// Also checks that the credentials of an application tested before in the same JVM are not reused
public class NoAgentAuthProviderTest {

    static final StubA2AAgent agent = new StubA2AAgent();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(StubA2AAgent.class));

    @AfterAll
    static void stopAgent() {
        agent.close();
    }

    @Test
    void agentRequestsCarryNoCredentials() {
        UntypedAgent remote = AgenticServices.a2aBuilder(agent.url()).inputKeys("input").build();

        assertThat(remote.invoke(Map.of("input", "hello"))).isEqualTo("pong");
        assertThat(agent.receivedAuthorization()).containsExactly("null");
        assertThat(agent.cardAuthorization()).containsExactly("null");
    }
}
