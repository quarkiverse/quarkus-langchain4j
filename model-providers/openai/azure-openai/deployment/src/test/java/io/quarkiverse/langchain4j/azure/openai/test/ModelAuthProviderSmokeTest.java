package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class ModelAuthProviderSmokeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Inject
    StreamingChatLanguageModel streamingChatLanguageModel;

    @Test
    void testChatLanguageModel() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(AzureOpenAiChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatLanguageModel)).isInstanceOf(AzureOpenAiStreamingChatModel.class);

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isNotBlank();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer token");
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
            return "Bearer token";
        }
    }
}
