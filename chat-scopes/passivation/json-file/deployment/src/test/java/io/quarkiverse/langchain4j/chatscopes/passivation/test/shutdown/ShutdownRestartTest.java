package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import static io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown.ShutdownTest.SHUTDOWN_TEST;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkus.test.QuarkusExtensionTest;

@Order(2)
public class ShutdownRestartTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            CounterBean.class));
    @Inject
    CounterBean counterBean;

    @Test
    void test() {
        ChatScope.activate(SHUTDOWN_TEST);
        Assertions.assertEquals(1, counterBean.getCounter());
        Assertions.assertTrue(counterBean.isPostActivateCalled());
        Assertions.assertEquals(1001, counterBean.getPassivationCounter());
        ChatScope.end();
    }

}
