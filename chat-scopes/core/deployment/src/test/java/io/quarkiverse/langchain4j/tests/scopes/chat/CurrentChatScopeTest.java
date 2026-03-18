package io.quarkiverse.langchain4j.tests.scopes.chat;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.PerChatScoped;
import io.quarkus.test.QuarkusUnitTest;

public class CurrentChatScopeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            ScopedCounterBean.class));

    @PerChatScoped
    public static class ScopedCounterBean {
        private int counter = 0;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }
    }

    @Inject
    ScopedCounterBean scopedCounterBean;

    @Test
    public void testScopedCounterBeanInherited() {
        try {
            scopedCounterBean.increment();
            Assertions.fail("Expected ContextNotActiveException");
        } catch (ContextNotActiveException e) {
        }

        ChatScope.begin();
        Assertions.assertEquals(0, scopedCounterBean.getCounter());
        scopedCounterBean.increment();
        Assertions.assertEquals(1, scopedCounterBean.getCounter());

        ChatScope.push();
        Assertions.assertEquals(0, scopedCounterBean.getCounter());
        scopedCounterBean.increment();
        Assertions.assertEquals(1, scopedCounterBean.getCounter());
        scopedCounterBean.increment();
        Assertions.assertEquals(2, scopedCounterBean.getCounter());

        ChatScope.pop();

        Assertions.assertEquals(1, scopedCounterBean.getCounter());

        ChatScope.end();
    }

}
