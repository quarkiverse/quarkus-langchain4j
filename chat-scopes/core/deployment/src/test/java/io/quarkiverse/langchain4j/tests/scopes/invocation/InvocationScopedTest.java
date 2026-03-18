package io.quarkiverse.langchain4j.tests.scopes.invocation;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class InvocationScopedTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(CounterBean.class, CalculateBean.class));

    @Inject
    CounterBean myBean;

    @Inject
    CalculateBean calculateBean;

    @Test
    void test() {
        myBean.increment();
        Assertions.assertEquals(0, myBean.getCounter());
        Assertions.assertEquals(2, calculateBean.add());
    }
}
