package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
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

    // Called for each request: every token it returns is a new one
    @ApplicationScoped
    public static class TokenPerAgent implements AgentAuthProvider {

        static final AtomicInteger calls = new AtomicInteger();

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token-" + calls.incrementAndGet() + "-for-" + input.agentName() + "@" + input.agentUrl();
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

    // Its customizer configures the JSON-RPC transport, which must not drop the credentials
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
            client.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
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
    void declarativeAgentSendsTheProvidersTokenToTheCardRequestAndEachMessage() {
        agent.receivedAuthorization().clear();

        assertThat(remoteAgent.ask("hello")).isEqualTo("pong");
        assertThat(remoteAgent.ask("hello again")).isEqualTo("pong");
        assertMessagesCarryFreshTokens(2);
        // A declarative agent may fetch its card before this test starts, so the card requests are not cleared
        assertThat(agent.cardAuthorization()).isNotEmpty().allMatch(
                header -> header.matches("Bearer token-\\d+-for-.+@" + Pattern.quote(agentUrl())));
    }

    @Test
    void programmaticAgentSendsTheProvidersTokenToTheCardRequestAndEachMessage() {
        clearRequests();
        UntypedAgent remote = AgenticServices.a2aBuilder(agentUrl()).name("programmatic").inputKeys("input").build();

        assertThat(remote.invoke(Map.of("input", "hello"))).isEqualTo("pong");
        assertThat(remote.invoke(Map.of("input", "hello again"))).isEqualTo("pong");
        // The Agent Card is not known while it is fetched: the provider receives the local agent's name
        assertThat(agent.cardAuthorization()).hasSize(1);
        assertThat(agent.cardAuthorization().get(0)).matches("Bearer token-\\d+-for-programmatic@" + Pattern.quote(agentUrl()));
        assertMessagesCarryFreshTokens(2);
    }

    @Test
    void customizerConfiguringTheJsonRpcTransportKeepsTheProvidersToken() {
        clearRequests();

        assertThat(customizedRemoteAgent.ask("hello")).isEqualTo("pong");
        assertMessagesCarryFreshTokens(1);
        assertThat(customizerCalls).hasPositiveValue();
    }

    private static void clearRequests() {
        agent.receivedAuthorization().clear();
        agent.cardAuthorization().clear();
    }

    // Each message carries the Secured Agent's token, and a different one each time
    private static void assertMessagesCarryFreshTokens(int messages) {
        assertThat(agent.receivedAuthorization()).hasSize(messages).doesNotHaveDuplicates()
                .allMatch(header -> header.matches(
                        "Bearer token-\\d+-for-Secured Agent@" + Pattern.quote(agentUrl())));
    }

    static String agentUrl() {
        return agent.url();
    }
}
