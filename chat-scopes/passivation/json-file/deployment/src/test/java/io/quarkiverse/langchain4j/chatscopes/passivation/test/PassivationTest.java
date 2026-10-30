package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class PassivationTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            CounterBean.class, AppScopedCounter.class, SingletonCounter.class));

    @Singleton
    public static class SingletonCounter {
        private int number = 0;

        public int getNumber() {
            return number;
        }

        public void increment() {
            number++;
        }

        public void clear() {
            number = 0;
        }

    }

    @ApplicationScoped
    public static class AppScopedCounter {
        private int number = 0;

        public int getNumber() {
            return number;
        }

        public void increment() {
            number++;
        }

        public void clear() {
            number = 0;
        }
    }

    @ChatScoped
    public static class CounterBean {
        private int counter = 0;

        @Inject
        AppScopedCounter appScopedCounter;

        @Inject
        SingletonCounter singletonCounter;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }

        public AppScopedCounter app() {
            return appScopedCounter;
        }

        public SingletonCounter singleton() {
            return singletonCounter;
        }
    }

    @Inject
    CounterBean counterBean;

    @Inject
    AppScopedCounter appScopedCounter;

    @Inject
    SingletonCounter singletonCounter;

    @Test
    public void testPassivation() throws Exception {
        ChatScope.begin();
        String id = ChatScope.id();
        Assertions.assertEquals(0, counterBean.getCounter());
        counterBean.increment();
        appScopedCounter.increment();
        singletonCounter.increment();
        Assertions.assertEquals(1, counterBean.getCounter());
        Assertions.assertEquals(1, counterBean.app().getNumber());
        Assertions.assertEquals(1, counterBean.singleton().getNumber());
        ChatScope.deactivate();

        ChatScopeManagedContext.INSTANCE.clear();
        appScopedCounter.clear();
        singletonCounter.clear();

        ChatScope.activate(id);
        Assertions.assertEquals(1, counterBean.getCounter());
        // make sure singleton and app scoped beans are not passivated and that proxy still works
        Assertions.assertEquals(0, counterBean.app().getNumber());
        Assertions.assertEquals(0, counterBean.singleton().getNumber());
        ChatScope.end();

        try {
            ChatScope.activate(id);
            Assertions.fail("scope should be removed");
        } catch (ContextNotActiveException ex) {
        }

    }
}
