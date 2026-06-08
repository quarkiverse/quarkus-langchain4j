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

/**
 * When a {@code ToolSearchStrategy} CDI bean exists, an AI service that does not opt out should let it narrow the
 * upfront tool catalog and add the tools the model discovers through the search tool.
 */
public class ToolSearchStrategyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToolSearchModel.class,
                            ToolSearchModelSupplier.class,
                            FakeToolSearchStrategy.class,
                            BookingTools.class,
                            ServiceWithToolSearch.class));

    @RegisterAiService(tools = BookingTools.class, chatLanguageModelSupplier = ToolSearchModelSupplier.class)
    interface ServiceWithToolSearch {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithToolSearch service;

    @Test
    @ActivateRequestContext
    void searchedToolIsExecuted() {
        String answer = service.chat("get my booking details", 1);
        assertEquals("REAL_TOOL_RESULT", answer);
    }

}
