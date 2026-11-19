package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.a2aproject.sdk.client.ClientBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider;
import io.quarkus.test.QuarkusExtensionTest;

public class AgentAuthProviderTest {

    static final StubA2AAgent agent = new StubA2AAgent();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StubA2AAgent.class, RemoteAgent.class, CustomizedRemoteAgent.class, TokenPerAgent.class));

    @ApplicationScoped
    public static class TokenPerAgent implements AgentAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer token-for-" + input.agentName() + "@" + input.agentUrl();
        }
    }

    public interface RemoteAgent {

        @A2AClientAgent(description = "A secured remote agent", outputKey = "reply")
        String ask(@V("input") String input);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return agentUrl();
        }
    }

    // Its customizer does not configure the transport, so the credentials are still added.
    public interface CustomizedRemoteAgent {

        @A2AClientAgent(description = "A secured remote agent, with a client customizer", outputKey = "reply")
        String ask(@V("input") String input);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return agentUrl();
        }

        @A2AClientCustomizer
        static void customize(ClientBuilder client) {
            customizerCalls.incrementAndGet();
        }
    }

    static final AtomicInteger customizerCalls = new AtomicInteger();

    @Inject
    RemoteAgent remoteAgent;

    @Inject
    CustomizedRemoteAgent customizedRemoteAgent;

    @AfterAll
    static void stopAgent() {
        agent.close();
    }

    @Test
    void declarativeAgentSendsTheProvidersToken() {
        agent.receivedAuthorization().clear();

        assertThat(remoteAgent.ask("hello")).isEqualTo("pong");
        assertThat(agent.receivedAuthorization()).containsExactly("Bearer token-for-Secured Agent@" + agentUrl());
    }

    @Test
    void programmaticAgentSendsTheProvidersToken() {
        agent.receivedAuthorization().clear();
        UntypedAgent remote = AgenticServices.a2aBuilder(agentUrl()).inputKeys("input").build();

        assertThat(remote.invoke(Map.of("input", "hello"))).isEqualTo("pong");
        assertThat(agent.receivedAuthorization()).containsExactly("Bearer token-for-Secured Agent@" + agentUrl());
    }

    @Test
    void customizedAgentSendsTheProvidersTokenAndRunsItsCustomizer() {
        agent.receivedAuthorization().clear();

        assertThat(customizedRemoteAgent.ask("hello")).isEqualTo("pong");
        assertThat(agent.receivedAuthorization()).containsExactly("Bearer token-for-Secured Agent@" + agentUrl());
        assertThat(customizerCalls).hasValue(1);
    }

    static String agentUrl() {
        return agent.url();
    }
}
