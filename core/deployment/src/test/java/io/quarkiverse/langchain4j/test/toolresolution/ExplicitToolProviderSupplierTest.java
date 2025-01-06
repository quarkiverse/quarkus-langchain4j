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
 * If an AI service specifies an explicit tool provider (and no specific tools),
 * that tool provider should be used.
 */
public class ExplicitToolProviderSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithExplicitToolProviderSupplier.class,
                            MyCustomToolProviderSupplier.class,
                            MyCustomToolProvider.class));

    @RegisterAiService(toolProviderSupplier = MyCustomToolProviderSupplier.class, chatLanguageModelSupplier = TestAiSupplier.class)
    interface ServiceWithExplicitToolProviderSupplier {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithExplicitToolProviderSupplier service;

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = service.chat("hello", 1);
        assertEquals("TOOL1", answer);
    }

}
