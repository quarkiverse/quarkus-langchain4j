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
 * If the AI service explicitly specifies tools, and there is a bean that implements ToolProvider,
 * but the service also declares a NoToolProviderSupplier, the explicit tools should be used.
 */
public class ExplicitToolsAndNoBeanToolProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithExplicitToolsAndNoToolProviderSupplier.class,
                            MyCustomToolProvider.class,
                            ToolsClass.class));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, tools = ToolsClass.class, toolProviderSupplier = RegisterAiService.NoToolProviderSupplier.class)
    interface ServiceWithExplicitToolsAndNoToolProviderSupplier {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @Inject
    ServiceWithExplicitToolsAndNoToolProviderSupplier service;

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = service.chat("hello", 1);
        assertEquals("\"EXPLICIT TOOL\"", answer);
    }

}
