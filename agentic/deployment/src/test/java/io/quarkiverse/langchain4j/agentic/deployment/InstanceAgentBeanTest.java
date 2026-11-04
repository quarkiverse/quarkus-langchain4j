package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies the beans with an instance agent method: a non-AI or human-in-the-loop agent invoked on the bean instance,
 * through its client proxy, with its helper methods as instance methods.
 */
public class InstanceAgentBeanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Logged {
    }

    @Interceptor
    @Logged
    @Priority(1)
    public static class LoggingInterceptor {

        static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

        @AroundInvoke
        public Object log(InvocationContext ctx) throws Exception {
            INVOCATIONS.add(ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    @ApplicationScoped
    public static class Policy {
        public boolean allows(String name) {
            return !"Voldemort".equals(name);
        }
    }

    /** No scope annotation: an application scoped bean. */
    public static class Greeter {

        @Inject
        Policy policy;

        @Agent(description = "Greets", outputKey = "greeting")
        @Logged
        public String greet(@V("name") String name) {
            return "Hello " + name;
        }

        @ActivationCondition
        boolean allowed(@V("name") String name) {
            return policy.allows(name);
        }
    }

    @ApplicationScoped
    public static class Approver {

        @HumanInTheLoop(description = "Asks for an approval", outputKey = "approved")
        @Logged
        public boolean approve(@V("greeting") String greeting) {
            return greeting.startsWith("Hello");
        }
    }

    public interface Greeting {

        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);
    }

    public interface Approval {

        @SequenceAgent(outputKey = "approved", subAgents = { Greeter.class, Approver.class })
        boolean approve(@V("name") String name);
    }

    @Inject
    Greeting greeting;

    @Inject
    Approval approval;

    @Inject
    Greeter greeter;

    @Test
    void instanceAgentBeansAreInvokedThroughTheirProxies() {
        LoggingInterceptor.INVOCATIONS.clear();
        assertThat(ClientProxy.unwrap(greeter)).isNotSameAs(greeter);

        assertThat(approval.approve("Mario")).isTrue();
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("greet", "approve");
    }

    @Test
    void instanceActivationCondition() {
        LoggingInterceptor.INVOCATIONS.clear();
        assertThat(greeting.greet("Mario")).isEqualTo("Hello Mario");
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("greet");

        LoggingInterceptor.INVOCATIONS.clear();
        // the instance activation condition of the bean skips the greeter
        assertThat(greeting.greet("Voldemort")).isNull();
        assertThat(LoggingInterceptor.INVOCATIONS).isEmpty();
    }
}
