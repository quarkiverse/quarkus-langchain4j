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
 * If the AI service explicitly specifies a NoToolProviderSupplier, then even if a ToolProvider
 * instance exists as a CDI bean, it should not be used.
 */
public class NoExplicitToolsAndNoToolProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithNoToolProvider.class,
                            MyCustomToolProvider.class));

    @RegisterAiService(toolProviderSupplier = RegisterAiService.NoToolProviderSupplier.class, chatLanguageModelSupplier = TestAiSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ServiceWithNoToolProvider {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @Inject
    ServiceWithNoToolProvider service;

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = service.chat("hello", 1);
        assertEquals("NO TOOL", answer);
    }

}
