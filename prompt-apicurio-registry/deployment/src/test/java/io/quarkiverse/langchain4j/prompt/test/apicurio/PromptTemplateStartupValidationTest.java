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
import io.quarkiverse.langchain4j.SystemMessageFromRegistry;
import io.quarkiverse.langchain4j.prompt.PromptTemplateValidationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the application fails to start when a registry template declares a required variable that is not bound
 * by the AI service method using it.
 */
public class PromptTemplateStartupValidationTest {

    static final String GROUP = "startup-validation-test";

    static final String TEMPLATE = """
            {
              "templateId": "greeting",
              "template": "Greet {{customerName}} on behalf of {{company}}",
              "variables": {
                "customerName": { "type": "string", "required": true },
                "company": { "type": "string", "required": true }
              }
            }
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> {
                ApicurioRegistryTestSupport.registryUrl();
                ApicurioRegistryTestSupport.createPromptTemplate(GROUP, "greeting", TEMPLATE, "application/json");
                return ShrinkWrap.create(JavaArchive.class)
                        .addClasses(ApicurioRegistryTestSupport.class, ApicurioRegistryTestSupport.EchoChatModel.class,
                                EchoChatModelSupplier.class, Greeter.class)
                        .addAsResource(new StringAsset(
                                "quarkus.langchain4j.prompt.apicurio-registry.url="
                                        + ApicurioRegistryTestSupport.registryUrl() + "\n"),
                                "application.properties");
            })
            .assertException(e -> {
                // the exception is loaded by the application class loader, so compare by name
                assertThat(e.getClass().getName()).isEqualTo(PromptTemplateValidationException.class.getName());
                assertThat(e.getMessage())
                        .contains(GROUP + "/greeting@branch=latest")
                        .contains("[customerName]")
                        .doesNotContain("company")
                        .contains(Greeter.class.getName() + "#greet");
            });

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface Greeter {

        @SystemMessageFromRegistry(value = "greeting", groupId = GROUP)
        String greet(String company);
    }

    @Singleton
    public static class EchoChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ApicurioRegistryTestSupport.EchoChatModel();
        }
    }

    @Inject
    Greeter greeter;

    @Test
    void shouldNotStart() {
        fail("Should not be called, see assertException");
    }
}
