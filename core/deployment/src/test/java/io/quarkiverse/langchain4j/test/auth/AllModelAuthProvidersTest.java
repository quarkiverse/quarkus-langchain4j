package io.quarkiverse.langchain4j.test.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.test.QuarkusUnitTest;

public class AllModelAuthProvidersTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(GeminiModelAuthProvider.class, OpenaiModelAuthProvider.class, GlobalModelAuthProvider.class));

    @Test
    void testThatGlobalModelAuthProviderIsSelectedWithNullModel() {
        assertTrue(ModelAuthProvider.resolve(null).get() instanceof GlobalModelAuthProvider);
    }

    @Test
    void testThatOpenAIModelAuthProviderIsSelectedForOpenaiModel() {
        assertTrue(ModelAuthProvider.resolve("openai").get() instanceof OpenaiModelAuthProvider);
    }

    @Test
    void testThatGeminiModelAuthProviderIsSelectedForGeminiModel() {
        assertTrue(ModelAuthProvider.resolve("gemini").get() instanceof GeminiModelAuthProvider);
    }
}
