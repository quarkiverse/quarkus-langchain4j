package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.a2a.A2AAgentCardException;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.mcp.McpToolResolutionException;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.AgentSkipped;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The {@link AgentListener} beans are registered, without any annotation on the agents, on the agents that
 * langchain4j-agentic builds without calling the configurator of the extension too: the static non-AI and
 * human-in-the-loop agents, the A2A and MCP client agents and the beans with an instance agent method.
 */
public class ListenerBeansOnAllAgentsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest();

    // ── Listener beans ──

    /**
     * What the listener beans are notified of, each under its own name.
     */
    @ApplicationScoped
    public static class Notifications {

        private final List<String> notifications = new CopyOnWriteArrayList<>();

        public void clear() {
            notifications.clear();
        }

        public void add(String listener, String event, String agentName) {
            notifications.add(listener + " " + event + " " + agentName);
        }

        public List<String> of(String listener) {
            List<String> listenerNotifications = new ArrayList<>();
            for (String notification : notifications) {
                if (notification.startsWith(listener + " ")) {
                    listenerNotifications.add(notification.substring(listener.length() + 1));
                }
            }
            return listenerNotifications;
        }
    }

    @ApplicationScoped
    public static class ListenerBean implements AgentListener {

        @Inject
        Notifications notifications;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            notifications.add("bean", "after", agentResponse.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            notifications.add("bean", "error", agentInvocationError.agentName());
        }

        @Override
        public void onAgentSkipped(AgentSkipped agentSkipped) {
            notifications.add("bean", "skipped", agentSkipped.agentName());
        }
    }

    /** Reaches a subagent twice, inherited from its parent and registered on it, and must be notified once. */
    @Singleton
    public static class InheritedListenerBean implements AgentListener {

        @Inject
        Notifications notifications;

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            notifications.add("inherited", "after", agentResponse.agentName());
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            notifications.add("inherited", "error", agentInvocationError.agentName());
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    // ── Static non-AI and human-in-the-loop agents ──

    public interface AudienceRequest {
        @HumanInTheLoop(name = "audienceRequest", description = "Asks the audience of a story", outputKey = "audience")
        static String ask(@V("topic") String topic) {
            return "kids";
        }
    }

    public static class RequestFormatter {
        @Agent(name = "requestFormatter", description = "Formats the request of a story", outputKey = "request")
        public static String format(@V("topic") String topic, @V("audience") String audience) {
            return "A story about " + topic + " for " + audience;
        }
    }

    public interface StoryRequest {
        @SequenceAgent(name = "storyRequest", outputKey = "request", subAgents = { AudienceRequest.class,
                RequestFormatter.class })
        String request(@V("topic") String topic);
    }

    /** A static non-AI agent with a listener of its own, which takes the place of the listener beans. */
    public static class Signature {

        static final List<String> OWN_LISTENER_NOTIFICATIONS = new CopyOnWriteArrayList<>();

        @Agent(name = "signature", description = "Signs a request", outputKey = "signed")
        public static String sign(@V("request") String request) {
            return request + ", signed";
        }

        @AgentListenerSupplier
        public static AgentListener listener() {
            return new AgentListener() {
                @Override
                public void afterAgentInvocation(AgentResponse agentResponse) {
                    OWN_LISTENER_NOTIFICATIONS.add(agentResponse.agentName());
                }
            };
        }
    }

    public interface SignedRequest {
        @SequenceAgent(name = "signedRequest", outputKey = "signed", subAgents = { RequestFormatter.class,
                Signature.class })
        String request(@V("topic") String topic, @V("audience") String audience);
    }

    // ── Remote agents, whose servers are down ──
    //
    // They are only invoked as subagents: langchain4j-agentic notifies the listener of an A2A or MCP client agent
    // when the agent runs as a subagent, not when it is invoked directly.

    public interface RemoteWriter {
        @A2AClientAgent(a2aServerUrl = "http://localhost:1", name = "remoteWriter", description = "Writes a story", outputKey = "story")
        String write(@V("request") String request);
    }

    public interface RemoteStory {
        @SequenceAgent(name = "remoteStory", outputKey = "story", subAgents = RemoteWriter.class)
        String write(@V("request") String request);
    }

    public interface Forecast {
        @McpClientAgent(toolName = "getWeatherForecast", name = "forecast", description = "Fetches the weather forecast", outputKey = "weather")
        String forecast(@V("destination") String destination);

        @McpClientSupplier
        static McpClient mcpClient() {
            // there isn't any MCP server
            return new DefaultMcpClient.Builder()
                    .transport(StreamableHttpMcpTransport.builder().url("http://localhost:1/mcp").build())
                    .build();
        }
    }

    public interface WeatherReport {
        @SequenceAgent(name = "weatherReport", outputKey = "weather", subAgents = Forecast.class)
        String report(@V("destination") String destination);
    }

    // ── Beans with an instance agent method, which nothing injects ──

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Logged {
    }

    @Interceptor
    @Logged
    @Priority(1)
    public static class LoggingInterceptor {

        static final List<String> INVOCATIONS = new CopyOnWriteArrayList<>();

        @AroundInvoke
        public Object log(InvocationContext ctx) throws Exception {
            INVOCATIONS.add(ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    /** No scope annotation: an application scoped bean. */
    public static class Greeter {
        @Agent(name = "greeter", description = "Greets", outputKey = "greeting")
        public String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    @Singleton
    public static class Shouter {
        @Agent(name = "shouter", description = "Shouts a greeting", outputKey = "shout")
        public String shout(@V("greeting") String greeting) {
            return greeting.toUpperCase();
        }
    }

    @ApplicationScoped
    public static class Signer {
        @Agent(name = "signer", description = "Signs a shout", outputKey = "signedShout")
        @Logged
        public String sign(@V("shout") String shout) {
            return shout + "!";
        }
    }

    @ApplicationScoped
    public static class Approver {
        @HumanInTheLoop(name = "approver", description = "Approves a shout", outputKey = "approved")
        public boolean approve(@V("signedShout") String signedShout) {
            return signedShout.startsWith("HELLO");
        }
    }

    public interface Greeting {
        @SequenceAgent(name = "greeting", outputKey = "approved", subAgents = { Greeter.class, Shouter.class, Signer.class,
                Approver.class })
        boolean greet(@V("name") String name);
    }

    /**
     * A dependent bean, so that each lookup gives another instance, with an instance helper method: its helper objects
     * must be the instance returned as subagent and the one of the listener beans, or its condition would be found
     * twice.
     */
    @Dependent
    public static class Censor {
        @Agent(name = "censor", description = "Censors a name", outputKey = "censored")
        public String censor(@V("name") String name) {
            return "***";
        }

        @ActivationCondition
        boolean forbidden(@V("name") String name) {
            return "Voldemort".equals(name);
        }
    }

    public interface Censorship {
        @SequenceAgent(name = "censorship", outputKey = "censored", subAgents = Censor.class)
        String censor(@V("name") String name);
    }

    @Inject
    StoryRequest storyRequest;

    @Inject
    SignedRequest signedRequest;

    @Inject
    RemoteStory remoteStory;

    @Inject
    WeatherReport weatherReport;

    @Inject
    Greeting greeting;

    @Inject
    Censorship censorship;

    @Inject
    Notifications notifications;

    @Test
    void listenerBeansAreNotifiedOfTheStaticNonAiAndHumanInTheLoopAgents() {
        notifications.clear();

        assertThat(storyRequest.request("dragons")).isEqualTo("A story about dragons for kids");

        assertThat(notifications.of("bean")).containsExactly("after audienceRequest", "after requestFormatter",
                "after storyRequest");
        // the inherited listener reaches the subagents twice, from the workflow and from the helper of the beans
        assertThat(notifications.of("inherited")).containsExactly("after audienceRequest", "after requestFormatter",
                "after storyRequest");
    }

    @Test
    void anAgentWithItsOwnListenerSupplierIsNotGivenTheListenerBeans() {
        notifications.clear();
        Signature.OWN_LISTENER_NOTIFICATIONS.clear();

        assertThat(signedRequest.request("dragons", "kids")).isEqualTo("A story about dragons for kids, signed");

        assertThat(Signature.OWN_LISTENER_NOTIFICATIONS).containsExactly("signature");
        assertThat(notifications.of("bean")).containsExactly("after requestFormatter", "after signedRequest");
        // the inherited listener still reaches the agent through the workflow
        assertThat(notifications.of("inherited")).containsExactly("after requestFormatter", "after signature",
                "after signedRequest");
    }

    @Test
    void listenerBeansAreNotifiedOfTheFailureOfAnA2AClientAgent() {
        notifications.clear();

        assertThatThrownBy(new ThrowingCallable() {
            @Override
            public void call() {
                remoteStory.write("a story about dragons");
            }
        }).isInstanceOf(AgentInvocationException.class)
                .hasStackTraceContaining(A2AAgentCardException.class.getName());

        assertThat(notifications.of("bean")).containsExactly("error remoteWriter", "error remoteStory");
        assertThat(notifications.of("inherited")).containsExactly("error remoteWriter", "error remoteStory");
    }

    @Test
    void listenerBeansAreNotifiedOfTheFailureOfAnMcpClientAgent() {
        notifications.clear();

        assertThatThrownBy(new ThrowingCallable() {
            @Override
            public void call() {
                weatherReport.report("Rome");
            }
        }).isInstanceOf(AgentInvocationException.class)
                .hasStackTraceContaining(McpToolResolutionException.class.getName());

        assertThat(notifications.of("bean")).containsExactly("error forecast", "error weatherReport");
        assertThat(notifications.of("inherited")).containsExactly("error forecast", "error weatherReport");
    }

    @Test
    void listenerBeansAreNotifiedOfTheBeansWithAnInstanceAgentMethod() {
        notifications.clear();
        LoggingInterceptor.INVOCATIONS.clear();

        assertThat(greeting.greet("Mario")).isTrue();

        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("sign");
        assertThat(notifications.of("bean")).containsExactly("after greeter", "after shouter", "after signer",
                "after approver", "after greeting");
        assertThat(notifications.of("inherited")).containsExactly("after greeter", "after shouter", "after signer",
                "after approver", "after greeting");
    }

    @Test
    void aDependentBeanWithAnInstanceActivationConditionIsSkippedWhenItsConditionIsFalse() {
        notifications.clear();

        assertThat(censorship.censor("Voldemort")).isEqualTo("***");

        assertThat(notifications.of("bean")).containsExactly("after censor", "after censorship");

        notifications.clear();

        assertThat(censorship.censor("Mario")).isNull();

        assertThat(notifications.of("bean")).containsExactly("skipped censor", "after censorship");
    }
}
