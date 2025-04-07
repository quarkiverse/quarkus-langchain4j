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
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

/**
 *
 */
public class ExplicitToolBoxAndProviderSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithToolClash.class,
                            MyCustomToolProviderSupplier.class,
                            MyCustomToolProvider.class,
                            ToolsClass.class));

    @RegisterAiService(toolProviderSupplier = MyCustomToolProviderSupplier.class, chatLanguageModelSupplier = TestAiSupplier.class)
    interface ServiceWithToolClash {

        @ToolBox(ToolsClass.class)
        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithToolClash service;

    @Test
    @ActivateRequestContext
    void testCall() {
        String answer = service.chat("hello", 1);
        assertEquals("\"EXPLICIT TOOL\"", answer);

    }

}
