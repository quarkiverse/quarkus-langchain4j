package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiNamedModelAuthProviderTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.my-gemini.base-url",
                    WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.my-gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    @ModelName("my-gemini")
    ChatModel chatModel;

    @Test
    void authProviderIsSelectedByConfigName() {
        wiremock().register(
                post(urlPathEqualTo(String.format(
                        "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent", CHAT_MODEL_ID)))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {"candidates":[{"content":{"role":"model","parts":[{"text":"Hi"}]},"finishReason":"STOP"}],
                                                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                                                """)));

        chatModel.chat("hello");

        assertThat(singleLoggedRequest().getHeader("Authorization")).isEqualTo("Bearer my-gemini-key");
    }

    @Singleton
    public static class DefaultAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer default-key";
        }
    }

    @Singleton
    @ModelName("my-gemini")
    public static class MyGeminiAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer my-gemini-key";
        }
    }
}
