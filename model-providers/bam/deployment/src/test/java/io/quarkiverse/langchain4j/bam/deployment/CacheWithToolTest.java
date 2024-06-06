package io.quarkiverse.langchain4j.bam.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class CacheWithToolTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(t -> {
                assertThat(t).isInstanceOf(RuntimeException.class);
                assertEquals("The cache cannot be used in combination with the tools. Affected class: %s"
                        .formatted(AiService.class.getName()), t.getMessage());
            });

    @RegisterAiService(tools = Object.class)
    public interface AiService {
        @CacheResult
        public String poem(@UserMessage("{text}") String text);
    }

    @Test
    void test() {
        fail("Should not be called");
    }
}
