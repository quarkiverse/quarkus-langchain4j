package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Check the tools usage when using the Request Scope
 */
public class ToolsRequestScopeTest extends ToolsScopeTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class,
                    MyToolImpl.class, MyApp.class));

    @Inject
    MyApp app;

    @Test
    @DisplayName("Verify tools invocation in the request scope without the scope active")
    void testWithoutRequestScopeActive() {
        if (Arc.container().requestContext().isActive()) {
            Arc.container().requestContext().deactivate();
        }
        assertThatThrownBy(() -> app.invoke(1))
                .isInstanceOf(ContextNotActiveException.class);
    }

    @Test
    @DisplayName("Verify tools invocation in the request scope")
    void testWithRequestSCope() {
        if (Arc.container().requestContext().isActive()) {
            Arc.container().requestContext().terminate();
        }

        Arc.container().requestContext().activate();
        app.invoke(1);
        app.invoke(2);
        Arc.container().requestContext().terminate();

        Arc.container().requestContext().activate();
        app.invoke(1);
        app.invoke(2);
        Arc.container().requestContext().terminate();
    }

    @RequestScoped
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
