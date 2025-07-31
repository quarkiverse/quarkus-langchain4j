package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatToolOne;
import io.quarkiverse.langchain4j.test.ToolnameValidation.ChatToolTwo;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyChatMemoryProvider;
import io.quarkiverse.langchain4j.test.ToolnameValidation.MyLanguageModel;
import io.quarkus.test.QuarkusUnitTest;

// Same method name for 2 tools used in same context is not ok
public class ToolnameValidationSameMethodNameTest {

    @RegisterAiService(tools = { ChatToolOne.class, ChatToolTwo.class })
    @ApplicationScoped
    public interface ToolnameConflict {

        @SystemMessage("You are a helpful assistant.")
        String chat(@UserMessage String message, int number);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToolnameConflict.class,
                            ChatToolOne.class, ChatToolTwo.class,
                            MyLanguageModel.class,
                            MyChatMemoryProvider.class))
            .assertException(arg0 -> {
                assertThat(arg0)
                        .isInstanceOf(DeploymentException.class)
                        .hasMessageContaining("Duplicate tool name 'chat' found");
            });

    @Test
    void test() {
        fail("Should not be called");
    }

}
