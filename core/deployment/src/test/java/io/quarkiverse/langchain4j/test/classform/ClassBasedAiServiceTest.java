package io.quarkiverse.langchain4j.test.classform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The class form of an AI service: a class annotated with {@code @RegisterAiService} whose AI methods are
 * {@code native}, which is an ordinary bean with injected fields, a lifecycle callback, interceptor bindings and
 * concrete methods next to the native ones.
 */
public class ClassBasedAiServiceTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, Greeting.class, EchoModelSupplier.class, Logged.class,
                            LoggingInterceptor.class));

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
        public Object log(InvocationContext context) throws Exception {
            INVOCATIONS.add(context.getMethod().getName());
            return context.proceed();
        }
    }

    @ApplicationScoped
    public static class Greeting {

        public String prefix() {
            return "Hello, ";
        }
    }

    /** Replies with the text of the last user message. */
    public static class EchoModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    var last = (dev.langchain4j.data.message.UserMessage) request.messages()
                            .get(request.messages().size() - 1);
                    return ChatResponse.builder().aiMessage(AiMessage.from(last.singleText())).build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoModelSupplier.class)
    public static class Assistant {

        @Inject
        Greeting greeting;

        private boolean initialized;

        @PostConstruct
        void init() {
            initialized = true;
        }

        @UserMessage("Say {{text}}")
        @Logged
        public native String say(@V("text") String text);

        public String greetAndSay(String text) {
            return greeting.prefix() + say(text);
        }

        @Logged
        public boolean isInitialized() {
            return initialized;
        }
    }

    @Inject
    Assistant assistant;

    @BeforeEach
    void reset() {
        LoggingInterceptor.INVOCATIONS.clear();
    }

    @Test
    @ActivateRequestContext
    void theNativeMethodsAreImplementedAndTheConcreteOnesCallThem() {
        assertThat(assistant.say("hi")).isEqualTo("Say hi");
        assertThat(assistant.greetAndSay("hi")).isEqualTo("Hello, Say hi");
    }

    @Test
    @ActivateRequestContext
    void theFieldsAreInjectedAndPostConstructRuns() {
        assertThat(assistant.isInitialized()).isTrue();
    }

    @Test
    @ActivateRequestContext
    void theInterceptorBindingsApplyToTheNativeAndToTheConcreteMethods() {
        assistant.say("hi");
        assistant.isInitialized();
        assertThat(LoggingInterceptor.INVOCATIONS).containsExactly("say", "isInitialized");
    }

    @Test
    @ActivateRequestContext
    void theBeanIsAGeneratedSubclassOfTheClass() {
        Object bean = ClientProxy.unwrap(assistant);
        assertThat(bean).isInstanceOf(Assistant.class);
        assertThat(bean.getClass()).isNotEqualTo(Assistant.class);
    }

    @Test
    void theClassCannotBeUsedWithoutInjection() {
        assertThatThrownBy(() -> new Assistant().say("hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be injected");
    }
}
