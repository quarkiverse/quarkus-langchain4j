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
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentInterceptorTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(LoggedAgent.class, Logged.class, Timed.class,
                            LoggingInterceptor.class, TimingInterceptor.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Logged {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Timed {
    }

    @Interceptor
    @Logged
    @Priority(1)
    public static class LoggingInterceptor {

        static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

        @AroundInvoke
        public Object log(InvocationContext ctx) throws Exception {
            INVOCATIONS.add("log:" + ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    @Interceptor
    @Timed
    @Priority(2)
    public static class TimingInterceptor {

        static final List<String> INVOCATIONS = Collections.synchronizedList(new ArrayList<>());

        @AroundInvoke
        public Object time(InvocationContext ctx) throws Exception {
            INVOCATIONS.add("time:" + ctx.getMethod().getName());
            return ctx.proceed();
        }
    }

    public interface LoggedAgent {

        @Logged
        @Timed
        @UserMessage("Answer: {{request}}")
        @Agent(description = "An agent with interceptor binding")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("intercepted response"))
                            .build();
                }
            };
        }
    }

    @Inject
    LoggedAgent agent;

    @BeforeEach
    void setUp() {
        LoggingInterceptor.INVOCATIONS.clear();
        TimingInterceptor.INVOCATIONS.clear();
    }

    @Test
    void interceptorFiresOnAgentMethodCall() {
        String result = agent.ask("hello");

        assertThat(result).isNotNull();
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("log:ask");
    }

    @Test
    void interceptorFiresOnEachCall() {
        agent.ask("first");
        agent.ask("second");

        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("log:ask", "log:ask");
    }

    @Test
    void multipleInterceptorsFireInPriorityOrder() {
        String result = agent.ask("hello");

        assertThat(result).isNotNull();
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("log:ask");
        assertThat(TimingInterceptor.INVOCATIONS).containsExactly("time:ask");
    }
}
