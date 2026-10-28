package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioPromptTemplateRegistry;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioSystemMessageProvider;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptTemplateReference;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ResolvedPromptTemplate;
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
              maxWords: 50
            ---
            Summarize the user message in {{maxWords}} words.
            """;

    static void seedRegistry() {
        ApicurioRegistryTestSupport.registryUrl();
        ApicurioRegistryTestSupport.createPromptTemplate(GROUP, "customer-support", SUPPORT_V1, "application/json");
        ApicurioRegistryTestSupport.createPromptTemplate(GROUP, "summarize", SUMMARIZE, "text/x-prompt-template");
        ApicurioRegistryTestSupport.createPromptTemplate("default", "plain-prompt-test",
                "You are a helpful assistant.", "text/x-prompt-template");
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> {
                seedRegistry();
                return ShrinkWrap.create(JavaArchive.class)
                        .addClasses(ApicurioRegistryTestSupport.class, ApicurioRegistryTestSupport.EchoChatModel.class,
                                EchoChatModelSupplier.class, SupportPrompt.class, PinnedSupportPrompt.class,
                                SummarizePrompt.class, PlainPrompt.class, SupportAssistant.class,
                                PinnedAssistant.class, Summarizer.class, PlainAssistant.class)
                        .addAsResource(new StringAsset(
                                "quarkus.langchain4j.prompt.apicurio-registry.url="
                                        + ApicurioRegistryTestSupport.registryUrl() + "\n"
                                        + "quarkus.langchain4j.prompt.apicurio-registry.cache-ttl=0\n"),
                                "application.properties");
            });

    public static class SupportPrompt extends ApicurioSystemMessageProvider {

        public SupportPrompt() {
            this(null);
        }

        protected SupportPrompt(String version) {
            super(GROUP, "customer-support", version);
        }

        @Override
        protected Map<String, Object> variables(InvocationContext context) {
            return Map.of("product", context.methodArguments().get(0));
        }
    }

    public static class PinnedSupportPrompt extends SupportPrompt {

        public PinnedSupportPrompt() {
            super("1");
        }
    }

    public static class SummarizePrompt extends ApicurioSystemMessageProvider {

        public SummarizePrompt() {
            super(GROUP, "summarize", null);
        }
    }

    public static class PlainPrompt extends ApicurioSystemMessageProvider {

        public PlainPrompt() {
            super("plain-prompt-test");
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = SupportPrompt.class)
    interface SupportAssistant {

        String chat(String product, @UserMessage String message);

        @SystemMessage("You are an inline assistant for {product}.")
        String inline(String product, @UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = PinnedSupportPrompt.class)
    interface PinnedAssistant {

        String chat(String product, @UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = SummarizePrompt.class)
    interface Summarizer {

        String summarize(String text);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = PlainPrompt.class)
    interface PlainAssistant {

        String chat(String message);
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
    ApicurioPromptTemplateRegistry registry;

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
    void explicitSystemMessageTakesPrecedence() {
        String response = supportAssistant.inline("Quarkus", "Hello");

        assertThat(response).isEqualTo("[system]You are an inline assistant for Quarkus.[user]Hello");
    }

    @Test
    @Order(3)
    @ActivateRequestContext
    void promptyDocumentDefaultsAreApplied() {
        String response = summarizer.summarize("some long text");

        assertThat(response).isEqualTo("[system]Summarize the user message in 50 words.[user]some long text");
    }

    @Test
    @Order(4)
    @ActivateRequestContext
    void plainTextTemplateWithDefaultGroupIsSupported() {
        String response = plainAssistant.chat("Ahoy");

        assertThat(response).isEqualTo("[system]You are a helpful assistant.[user]Ahoy");
    }

    @Test
    @Order(5)
    void registryBeanIsInjectableAndExposesVariables() {
        ResolvedPromptTemplate resolved = registry.resolve(PromptTemplateReference.of(GROUP, "customer-support", ""));

        assertThat(resolved.version()).isEqualTo("1");
        assertThat(resolved.variables()).containsKeys("product", "tone");
        assertThat(resolved.variables().get("product").required()).isTrue();
    }

    @Test
    @Order(6)
    @ActivateRequestContext
    void newVersionsAreServedAfterRefreshWhilePinnedVersionsAreStable() {
        ApicurioRegistryTestSupport.createPromptTemplateVersion(GROUP, "customer-support", SUPPORT_V2, "application/json");

        // the cache never expires in this test (cache-ttl=0), so the old version is still served
        assertThat(supportAssistant.chat("Quarkus", "Hi")).startsWith("[system]You are a support assistant");

        registry.invalidate();

        assertThat(supportAssistant.chat("Quarkus", "Hi"))
                .isEqualTo("[system]You are the v2 support assistant for Quarkus. Be friendly.[user]Hi");
        assertThat(pinnedAssistant.chat("Quarkus", "Hi"))
                .isEqualTo("[system]You are a support assistant for Quarkus. Be friendly.[user]Hi");
    }
}
