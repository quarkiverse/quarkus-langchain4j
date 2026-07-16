package io.quarkiverse.langchain4j.test.toolsearch;

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

public class ToolConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToolSearchModel.class,
                            ToolSearchModelSupplier.class,
                            FakeToolSearchStrategy.class,
                            BookingTools.class,
                            ConfiguredService.class));

    @RegisterAiService(chatLanguageModelSupplier = ToolSearchModelSupplier.class, tools = BookingTools.class, toolSearchStrategy = FakeToolSearchStrategy.class)
    interface ConfiguredService {

        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @Inject
    ConfiguredService service;

    @Test
    @ActivateRequestContext
    void configuresToolsAndSearchStrategyUsingBeanClasses() {
        assertEquals("REAL_TOOL_RESULT", service.chat("get my booking details", 1));
    }
}
