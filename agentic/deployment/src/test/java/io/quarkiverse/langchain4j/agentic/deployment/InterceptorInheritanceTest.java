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
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that an interceptor binding on a parent interface class declaration is detected by
 * {@code hasAnyInterceptorBindings} so that {@code injectInterceptionProxy()} is called and
 * interceptors fire on the agent.
 * <p>
 * Scenario: {@code @Traced} is declared at <em>class level</em> on a parent
 * interface {@code TracedBase}. The agent interface {@code ChildAgent} extends {@code TracedBase}
 * but does <em>not</em> redeclare {@code @Traced}. The full interface hierarchy must be walked
 * to detect the binding and enable interception.
 */
public class InterceptorInheritanceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Traced.class, TracingInterceptor.class, TracedBase.class,
                            ChildAgent.class, FixedChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    /** Custom interceptor binding declared at class level on the BASE interface. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Traced {
    }

    @Interceptor
    @Traced
    @Priority(1)
    public static class TracingInterceptor {
        static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());

        @AroundInvoke
        public Object trace(InvocationContext ctx) throws Exception {
            CALLS.add(ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    /**
     * Parent interface. {@code @Traced} is declared here at class level, <em>not</em> on
     * {@code ChildAgent}.
     */
    @Traced
    public interface TracedBase {
    }

    /**
     * Agent that extends {@code TracedBase}. Does NOT redeclare {@code @Traced}.
     * The interceptor binding must be found by traversing the interface hierarchy.
     */
    public interface ChildAgent extends TracedBase {
        @UserMessage("{{q}}")
        @Agent(description = "Agent whose parent interface carries the interceptor binding")
        String answer(String q);

        @ChatModelSupplier
        static ChatModel model() {
            return new FixedChatModel();
        }
    }

    /** Simple chat model that returns a fixed response immediately. */
    public static class FixedChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
        }
    }

    @Inject
    ChildAgent childAgent;

    @BeforeEach
    void setUp() {
        TracingInterceptor.CALLS.clear();
    }

    @Test
    void interceptorOnParentInterfaceClassLevelIsApplied() {
        // The full interface hierarchy is walked to find @Traced on TracedBase,
        // so hasAnyInterceptorBindings returns true and injectInterceptionProxy()
        // is called, enabling the interceptor.
        String result = childAgent.answer("hello");
        assertThat(result).isNotNull();
        assertThat(TracingInterceptor.CALLS)
                .as("TracingInterceptor should fire: @Traced is declared at class level on parent interface TracedBase")
                .containsExactly("answer");
    }
}
