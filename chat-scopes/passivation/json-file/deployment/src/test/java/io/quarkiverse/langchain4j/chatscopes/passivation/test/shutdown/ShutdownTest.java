package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

@Order(1)
public class ShutdownTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            CounterBean.class));
    public static final String SHUTDOWN_TEST = "shutdown-test";
    @Inject
    CounterBean counterBean;

    @Test
    void test() {
        ChatScopeManagedContext.INSTANCE.begin(SHUTDOWN_TEST, null);
        ChatScope.end(); // we make sure this is a clean test
        ChatScopeManagedContext.INSTANCE.begin(SHUTDOWN_TEST, null);
        Assertions.assertEquals(0, counterBean.getCounter());
        System.out.println("first counter=" + counterBean.getCounter());
        counterBean.increment();
        System.out.println("first increment=" + counterBean.getCounter());
        ChatScope.deactivate();
    }

}
