package io.quarkiverse.langchain4j.test.toolresolution;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 *
 */
public class ExplicitToolsAndProviderSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            ServiceWithToolClash.class,
                            MyCustomToolProviderSupplier.class,
                            MyCustomToolProvider.class,
                            ToolsClass.class));

    @RegisterAiService(toolProviderSupplier = MyCustomToolProviderSupplier.class, tools = ToolsClass.class, chatLanguageModelSupplier = TestAiSupplier.class)
    interface ServiceWithToolClash {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithToolClash service;

    @Test
    @ActivateRequestContext
    void testCall() {
        try {
            String answer = service.chat("hello", 1);
            Assertions.fail("Exception expected");
        } catch (Exception e) {
            Assertions.assertThat(e.getMessage()).contains(" Cannot use a tool provider when explicit tools are provided");
        }
    }

}
