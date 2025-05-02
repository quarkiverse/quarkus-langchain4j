package io.quarkiverse.langchain4j.test.tools;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class ToolBoxWithNoMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAiService.class, Tools.class))
            .setExpectedException(IllegalArgumentException.class);

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface MyAiService {

        @ToolBox(Tools.class)
        String chat(String userMessage);
    }

    @Singleton
    public static class Tools {

        @Tool
        public String someTool(String foo) {
            return "bar";
        }
    }

}
