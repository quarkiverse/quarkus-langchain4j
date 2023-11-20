package org.acme.examples.aiservices;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Check the tools usage when using the ApplicationScoped
 */
public class ToolsApplicationScopedTest extends ToolsScopeTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class,
                    MyToolImpl.class, MyApp.class));

    @Inject
    MyApp app;

    @Test
    @DisplayName("Verify tools invocation in the application scope")
    void test() {
        app.invoke(1);
        app.invoke(2);
    }

    @ApplicationScoped
    public static class MyToolImpl implements MyTool {
        private final AtomicInteger called = new AtomicInteger();

        @Tool
        public String doSomething(String content) {
            called.incrementAndGet();
            return "ignored";
        }

        public int called() {
            return called.get();
        }
    }
}
