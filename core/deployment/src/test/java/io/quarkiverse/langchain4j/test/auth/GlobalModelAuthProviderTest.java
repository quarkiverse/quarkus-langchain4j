package io.quarkiverse.langchain4j.test.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.test.QuarkusUnitTest;

public class GlobalModelAuthProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(GlobalModelAuthProvider.class));

    @Test
    void testThatGlobalModelAuthProviderIsSelectedWithNullModel() {
        assertTrue(ModelAuthProvider.resolve(null).get() instanceof GlobalModelAuthProvider);
    }

    @Test
    void testThatGlobalOpenAIModelAuthProviderIsSelectedForOpenaiModel() {
        assertTrue(ModelAuthProvider.resolve("openai").get() instanceof GlobalModelAuthProvider);
    }

    @Test
    void testThatGlobalModelAuthProviderIsSelectedForGeminiModel() {
        assertTrue(ModelAuthProvider.resolve("gemini").get() instanceof GlobalModelAuthProvider);
    }
}
