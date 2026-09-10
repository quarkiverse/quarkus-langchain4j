package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
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
import io.quarkiverse.langchain4j.prompt.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.PromptTemplateValidationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * With {@code validate-on-startup=false}, templates are resolved lazily and problems surface when the AI service is
 * invoked.
 */
public class PromptTemplateLazyResolutionTest {

    static final String GROUP = "lazy-resolution-test";

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
                                EchoChatModelSupplier.class, Greeter.class, MissingTemplateAssistant.class)
                        .addAsResource(new StringAsset(
                                "quarkus.langchain4j.prompt.apicurio-registry.url="
                                        + ApicurioRegistryTestSupport.registryUrl() + "\n"
                                        + "quarkus.langchain4j.prompt.apicurio-registry.validate-on-startup=false\n"),
                                "application.properties");
            });

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface Greeter {

        @SystemMessageFromRegistry(value = "greeting", groupId = GROUP)
        String greet(String company);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MissingTemplateAssistant {

        @SystemMessageFromRegistry(value = "does-not-exist", groupId = "nowhere")
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
    Greeter greeter;

    @Inject
    MissingTemplateAssistant missingTemplateAssistant;

    @Test
    @ActivateRequestContext
    void missingRequiredVariableIsReportedOnInvocation() {
        assertThatThrownBy(() -> greeter.greet("ACME"))
                .isInstanceOf(PromptTemplateValidationException.class)
                .hasMessageContaining("[customerName]");
    }

    @Test
    @ActivateRequestContext
    void unknownArtifactIsReportedOnInvocation() {
        assertThatThrownBy(() -> missingTemplateAssistant.chat("hi"))
                .isInstanceOf(PromptTemplateResolutionException.class)
                .hasMessageContaining("nowhere/does-not-exist@branch=latest");
        assertThat(missingTemplateAssistant).isNotNull();
    }
}
