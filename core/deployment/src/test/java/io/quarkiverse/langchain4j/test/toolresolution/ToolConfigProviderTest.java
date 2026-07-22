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

public class ToolConfigProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class, TestAiModel.class, ConfiguredService.class,
                            CombinedConfigService.class, DefaultConfigService.class, MyCustomToolProvider.class,
                            ToolsClass.class));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, toolProvider = MyCustomToolProvider.class)
    interface ConfiguredService {

        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, toolProvider = MyCustomToolProvider.class, tools = ToolsClass.class)
    interface CombinedConfigService {
        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class)
    interface DefaultConfigService {
        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @Inject
    ConfiguredService service;

    @Inject
    CombinedConfigService combinedService;

    @Inject
    DefaultConfigService defaultConfigService;

    @Test
    @ActivateRequestContext
    void configuresProviderUsingBeanClass() {
        assertEquals("TOOL1", service.chat("hello", 1));
    }

    @Test
    @ActivateRequestContext
    void combinesStaticToolsAndProviderUsingExistingPrecedence() {
        assertEquals("\"EXPLICIT TOOL\"", combinedService.chat("hello", 2));
    }

    @Test
    @ActivateRequestContext
    void preservesDefaultToolConfigurationWhenNoAttributesAreSet() {
        assertEquals("TOOL1", defaultConfigService.chat("hello", 3));
    }
}
