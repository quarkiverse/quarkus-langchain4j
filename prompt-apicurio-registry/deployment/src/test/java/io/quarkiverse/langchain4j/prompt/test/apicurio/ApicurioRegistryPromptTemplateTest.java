package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.SystemMessageFromRegistry;
import io.quarkiverse.langchain4j.UserMessageFromRegistry;
import io.quarkiverse.langchain4j.prompt.PromptTemplateReference;
import io.quarkiverse.langchain4j.prompt.PromptTemplateRegistry;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioPromptTemplateRegistry;
import io.quarkus.test.QuarkusUnitTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApicurioRegistryPromptTemplateTest {

    static final String GROUP = "prompt-template-test";

    static final String SUPPORT_V1 = """
            {
              "templateId": "customer-support",
              "template": "You are a support assistant for {{product}}. Be {{tone}}.",
              "variables": {
                "product": { "type": "string", "required": true },
                "tone": { "type": "string", "default": "friendly" }
              }
            }
            """;

    static final String SUPPORT_V2 = """
            {
              "templateId": "customer-support",
              "template": "You are the v2 support assistant for {{product}}. Be {{tone}}.",
              "variables": {
                "product": { "type": "string", "required": true },
                "tone": { "type": "string", "default": "friendly" }
              }
            }
            """;

    static final String SUMMARIZE = """
            ---
            name: Summarize
            inputs:
              text:
                type: string
                required: true
              maxWords: 50
            ---
            Summarize in {{maxWords}} words: {{text}}
            """;

    static void seedRegistry() {
        ApicurioRegistryTestSupport.registryUrl();
        ApicurioRegistryTestSupport.createPromptTemplate(GROUP, "customer-support", SUPPORT_V1, "application/json");
        ApicurioRegistryTestSupport.createPromptTemplate(GROUP, "summarize", SUMMARIZE, "text/x-prompt-template");
        ApicurioRegistryTestSupport.createPromptTemplate("default", "plain-prompt-test",
                "You are a {{role}}.", "text/x-prompt-template");
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> {
                seedRegistry();
                return ShrinkWrap.create(JavaArchive.class)
                        .addClasses(ApicurioRegistryTestSupport.class, ApicurioRegistryTestSupport.EchoChatModel.class,
                                EchoChatModelSupplier.class, SupportAssistant.class, Summarizer.class,
                                PinnedAssistant.class, PlainAssistant.class)
                        .addAsResource(new StringAsset(
                                "quarkus.langchain4j.prompt.apicurio-registry.url="
                                        + ApicurioRegistryTestSupport.registryUrl() + "\n"
                                        + "quarkus.langchain4j.prompt.apicurio-registry.cache-ttl=0\n"),
                                "application.properties");
            });

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface SupportAssistant {

        @SystemMessageFromRegistry(value = "customer-support", groupId = GROUP)
        String chat(@V("product") String product, @UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface PinnedAssistant {

        @SystemMessageFromRegistry(value = "customer-support", groupId = GROUP, version = "1")
        String chat(@V("product") String product, @UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface Summarizer {

        @UserMessageFromRegistry(value = "summarize", groupId = GROUP)
        String summarize(String text);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @SystemMessageFromRegistry("plain-prompt-test")
    interface PlainAssistant {

        String chat(@V("role") String role, @UserMessage String message);
    }

    @Singleton
    public static class EchoChatModelSupplier implements Supplier<ChatModel> {

        private final ChatModel model = new ApicurioRegistryTestSupport.EchoChatModel();

        @Override
        public ChatModel get() {
            return model;
        }
    }

    @Inject
    SupportAssistant supportAssistant;

    @Inject
    PinnedAssistant pinnedAssistant;

    @Inject
    Summarizer summarizer;

    @Inject
    PlainAssistant plainAssistant;

    @Inject
    PromptTemplateRegistry registry;

    @Test
    @Order(1)
    @ActivateRequestContext
    void systemMessageIsLoadedFromRegistryAndDefaultsAreApplied() {
        String response = supportAssistant.chat("Quarkus", "Hello");

        assertThat(response).isEqualTo("[system]You are a support assistant for Quarkus. Be friendly.[user]Hello");
    }

    @Test
    @Order(2)
    @ActivateRequestContext
    void userMessageIsLoadedFromPromptyDocument() {
        String response = summarizer.summarize("some long text");

        assertThat(response).isEqualTo("[user]Summarize in 50 words: some long text");
    }

    @Test
    @Order(3)
    @ActivateRequestContext
    void classLevelAnnotationAndPlainTextTemplateAreSupported() {
        String response = plainAssistant.chat("pirate", "Ahoy");

        assertThat(response).isEqualTo("[system]You are a pirate.[user]Ahoy");
    }

    @Test
    @Order(4)
    void registryBeanIsInjectableAndExposesVariables() {
        assertThat(registry).isInstanceOf(ApicurioPromptTemplateRegistry.class);
        ResolvedPromptTemplate resolved = registry.resolve(PromptTemplateReference.of(GROUP, "customer-support", ""));

        assertThat(resolved.version()).isEqualTo("1");
        assertThat(resolved.variables()).containsKeys("product", "tone");
        assertThat(resolved.variables().get("product").required()).isTrue();
    }

    @Test
    @Order(5)
    @ActivateRequestContext
    void newVersionsAreServedAfterRefreshWhilePinnedVersionsAreStable() {
        ApicurioRegistryTestSupport.createPromptTemplateVersion(GROUP, "customer-support", SUPPORT_V2, "application/json");

        // the cache never expires in this test (cache-ttl=0), so the old version is still served
        assertThat(supportAssistant.chat("Quarkus", "Hi")).startsWith("[system]You are a support assistant");

        ((ApicurioPromptTemplateRegistry) registry).invalidate();

        assertThat(supportAssistant.chat("Quarkus", "Hi"))
                .isEqualTo("[system]You are the v2 support assistant for Quarkus. Be friendly.[user]Hi");
        assertThat(pinnedAssistant.chat("Quarkus", "Hi"))
                .isEqualTo("[system]You are a support assistant for Quarkus. Be friendly.[user]Hi");
    }
}
