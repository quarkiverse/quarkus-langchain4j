package io.quarkiverse.langchain4j.test.toolresolution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * If the AI service explicitly specifies tools AND there is a bean that implements ToolProvider,
 * the explicit tools should be used, and the ToolProvider bean should be ignored.
 */
public class ExplicitToolsWhenBeanToolProviderExistsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithExplicitTools.class,
                            MyCustomToolProvider.class,
                            ToolsClass.class));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, tools = ToolsClass.class)
    interface ServiceWithExplicitTools {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @Inject
    ServiceWithExplicitTools service;

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = service.chat("hello", 1);
        assertEquals("\"EXPLICIT TOOL\"", answer);
    }

}
