package io.quarkiverse.langchain4j.test;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatToolOne;
import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatterTools;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyChatMemoryProvider;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyLanguageModel;
import io.quarkus.test.QuarkusUnitTest;

// Same method name for 2 tools used in same context is not ok
public class ToolnameValidationSameMethodNameDifferentToolnameTest {

    @RegisterAiService(tools = { ChatToolOne.class, ChatterTools.class })
    @ApplicationScoped
    public interface NoSameNameConflict {

        @SystemMessage("You are a helpful assistant.")
        String chat(@UserMessage String message, int number);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            NoSameNameConflict.class, ChatToolOne.class,
                            ChatterTools.class,
                            MyLanguageModel.class,
                            MyChatMemoryProvider.class));

    @Test
    void test() {
    }
}
