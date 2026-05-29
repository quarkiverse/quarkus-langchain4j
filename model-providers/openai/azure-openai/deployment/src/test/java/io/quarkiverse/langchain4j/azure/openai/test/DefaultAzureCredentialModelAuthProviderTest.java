package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultAzureCredentialModelAuthProviderTest extends OpenAiBaseTest {

    private static final String MOCK_TOKEN = "mock-azure-bearer-token";

    @ApplicationScoped
    public static class MockModelAuthProvider implements ModelAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + MOCK_TOKEN;
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MockModelAuthProvider.class))
            .overrideConfigKey(
                    "quarkus.langchain4j.azure-openai.use-azure-credential-model-auth-provider", "true")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    ModelAuthProvider modelAuthProvider;

    @Test
    void authProviderBeanIsRegisteredAndResolvable() {
        assertThat(modelAuthProvider).isInstanceOf(MockModelAuthProvider.class);
    }

    @Test
    void chatUsesAuthProviderBearerToken() {
        String response = chatModel.chat("hello");
        assertThat(response).isNotBlank();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization"))
                .isEqualTo("Bearer " + MOCK_TOKEN);
    }
}