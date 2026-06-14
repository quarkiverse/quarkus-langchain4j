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
 * Even when a {@code ToolSearchStrategy} CDI bean exists, a service that does not set
 * {@code toolSearchStrategy} (default {@code void.class} = SKIP) must keep the full tool catalog visible upfront.
 */
public class ToolSearchOptOutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToolSearchModel.class,
                            FakeToolSearchStrategy.class,
                            BookingTools.class,
                            ServiceOptingOut.class));

    @RegisterAiService(tools = BookingTools.class)
    interface ServiceOptingOut {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceOptingOut service;

    @Test
    @ActivateRequestContext
    void catalogIsNotNarrowedWhenOptedOut() {
        String answer = service.chat("get my booking details", 1);
        assertEquals("TOOL_NOT_NARROWED", answer);
    }

}
