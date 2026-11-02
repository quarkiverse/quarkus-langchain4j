package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioSystemMessageProvider;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptTemplateValidationException;
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
                                EchoChatModelSupplier.class, GreetingPrompt.class, MissingPrompt.class, Greeter.class,
                                MissingTemplateAssistant.class)
                        .addAsResource(new StringAsset(
                                "quarkus.langchain4j.prompt.apicurio-registry.url="
                                        + ApicurioRegistryTestSupport.registryUrl() + "\n"
                                        + "quarkus.langchain4j.prompt.apicurio-registry.validate-on-startup=false\n"),
                                "application.properties");
            });

    public static class GreetingPrompt extends ApicurioSystemMessageProvider {

        public GreetingPrompt() {
            super(GROUP, "greeting", null);
        }

        @Override
        protected Map<String, Object> variables(InvocationContext context) {
            return Map.of("company", context.methodArguments().get(0));
        }
    }

    public static class MissingPrompt extends ApicurioSystemMessageProvider {

        public MissingPrompt() {
            super("nowhere", "does-not-exist", null);
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = GreetingPrompt.class)
    interface Greeter {

        String greet(String company);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = MissingPrompt.class)
    interface MissingTemplateAssistant {

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
                .hasMessageContaining("[customerName]")
                .hasMessageContaining(GreetingPrompt.class.getName());
    }

    @Test
    @ActivateRequestContext
    void unknownArtifactIsReportedOnInvocation() {
        assertThatThrownBy(() -> missingTemplateAssistant.chat("hi"))
                .isInstanceOf(PromptTemplateResolutionException.class)
                .hasMessageContaining("nowhere/does-not-exist@branch=latest");
    }
}
