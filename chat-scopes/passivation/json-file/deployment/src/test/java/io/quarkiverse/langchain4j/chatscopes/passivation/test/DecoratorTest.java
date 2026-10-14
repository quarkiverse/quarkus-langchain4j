package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class DecoratorTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            CounterBean.class, Counter10.class, Counter100.class, Counter.class));

    public interface Counter {
        void increment();

        int getCounter();
    }

    @Decorator
    public static class Counter10 implements Counter {
        @Inject
        @Delegate
        Counter delegate;

        int counter;

        public Counter10() {
        }

        @Override
        public void increment() {
            counter += 10;
            delegate.increment();
        }

        @Override
        public int getCounter() {
            return delegate.getCounter() + counter;
        }
    }

    @Decorator
    public static class Counter100 implements Counter {
        @Inject
        @Delegate
        Counter delegate;

        int counter;

        public Counter100() {
        }

        @Override
        public void increment() {
            counter += 100;
            delegate.increment();
        }

        @Override
        public int getCounter() {
            return delegate.getCounter() + counter;
        }
    }

    @ChatScoped
    public static class CounterBean implements Counter {
        private int counter = 0;

        @Override
        public void increment() {
            counter++;
        }

        @Override
        public int getCounter() {
            return counter;
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
        Assertions.assertEquals(111, counterBean.getCounter());
        ChatScope.deactivate();

        ChatScopeManagedContext.INSTANCE.clear();

        ChatScope.activate(id);
        Assertions.assertEquals(111, counterBean.getCounter());
        ChatScope.end();

        try {
            ChatScope.activate(id);
            Assertions.fail("scope should be removed");
        } catch (ContextNotActiveException ex) {
        }

    }

}
