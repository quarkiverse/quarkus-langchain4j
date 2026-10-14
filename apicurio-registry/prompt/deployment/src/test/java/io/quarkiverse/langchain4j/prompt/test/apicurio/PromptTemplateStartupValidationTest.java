package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioSystemMessageProvider;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptTemplateResolutionException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the application fails to start when the template of an {@link ApicurioSystemMessageProvider} cannot be
 * resolved from the registry.
 */
public class PromptTemplateStartupValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ApicurioRegistryTestSupport.class, ApicurioRegistryTestSupport.EchoChatModel.class,
                            EchoChatModelSupplier.class, MissingPrompt.class, Assistant.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.prompt.apicurio-registry.url="
                                    + ApicurioRegistryTestSupport.registryUrl() + "\n"),
                            "application.properties"))
            .assertException(e -> {
                // the exception is loaded by the application class loader, so compare by name
                assertThat(e.getClass().getName()).isEqualTo(PromptTemplateResolutionException.class.getName());
                assertThat(e.getMessage()).contains("startup-validation-test/does-not-exist@branch=latest");
            });

    public static class MissingPrompt extends ApicurioSystemMessageProvider {

        public MissingPrompt() {
            super("startup-validation-test", "does-not-exist", null);
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = MissingPrompt.class)
    interface Assistant {

        String chat(String message);
    }

    @Singleton
    public static class EchoChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ApicurioRegistryTestSupport.EchoChatModel();
        }
    }

    @Inject
    Assistant assistant;

    @Test
    void shouldNotStart() {
        fail("Should not be called, see assertException");
    }
}
