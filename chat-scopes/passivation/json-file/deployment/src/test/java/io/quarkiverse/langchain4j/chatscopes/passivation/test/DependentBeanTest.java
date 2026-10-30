package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import java.lang.annotation.*;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class DependentBeanTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            CounterBean.class, DependentCounter.class, NestedCounter.class));

    @Documented
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @InterceptorBinding
    public @interface Plus1000 {

    }

    @Interceptor
    @Plus1000
    public static class MyInterceptor {
        @AroundInvoke
        public Object intercept(InvocationContext ctx) throws Exception {
            System.out.println("Interceptor called: ");
            int val = (Integer) ctx.proceed();
            return val + 1000;
        }
    }

    @Dependent
    public static class DependentCounter {
        private int number = 0;

        @Inject
        NestedCounter nested;

        public int getNumber() {
            return number;
        }

        public void increment() {
            number++;
        }

        public NestedCounter nested() {
            return nested;
        }

        @Plus1000
        public int intercepted() {
            return number;
        }
    }

    @Dependent
    public static class NestedCounter {
        private int number = 0;

        public int getNumber() {
            return number;
        }

        public void increment() {
            number++;
        }
    }

    @ChatScoped
    public static class CounterBean {
        private int counter = 0;

        @Inject
        DependentCounter dependentCounter;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }

        public DependentCounter dependent() {
            return dependentCounter;
        }
    }

    @Inject
    CounterBean counterBean;

    @Test
    public void testPassivation() throws Exception {
        ChatScope.begin();
        String id = ChatScope.id();
        Assertions.assertEquals(0, counterBean.getCounter());
        counterBean.increment();
        counterBean.dependent().increment();
        counterBean.dependent().nested().increment();
        Assertions.assertEquals(1, counterBean.getCounter());
        Assertions.assertEquals(1, counterBean.dependent().getNumber());
        Assertions.assertEquals(1001, counterBean.dependent().intercepted());
        Assertions.assertEquals(1, counterBean.dependent().nested().getNumber());
        ChatScope.deactivate();

        ChatScopeManagedContext.INSTANCE.clear();

        ChatScope.activate(id);
        Assertions.assertEquals(1, counterBean.getCounter());
        Assertions.assertEquals(1, counterBean.dependent().getNumber());
        Assertions.assertEquals(1001, counterBean.dependent().intercepted());
        Assertions.assertEquals(1, counterBean.dependent().nested().getNumber());
        ChatScope.end();

        try {
            ChatScope.activate(id);
            Assertions.fail("scope should be removed");
        } catch (ContextNotActiveException ex) {
        }

    }
}
