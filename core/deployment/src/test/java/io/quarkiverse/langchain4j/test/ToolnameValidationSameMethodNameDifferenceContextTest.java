package io.quarkiverse.langchain4j.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatToolOne;
import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatToolTwo;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyChatMemoryProvider;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyLanguageModel;
import io.quarkus.test.QuarkusUnitTest;

// Same method name for 2 tools used in same context is not ok
public class ToolnameValidationSameMethodNameDifferenceContextTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ChatToolOne.class,
                            ChatToolTwo.class,
                            MyLanguageModel.class,
                            MyChatMemoryProvider.class));

    @Test
    void test() {
    }

}
