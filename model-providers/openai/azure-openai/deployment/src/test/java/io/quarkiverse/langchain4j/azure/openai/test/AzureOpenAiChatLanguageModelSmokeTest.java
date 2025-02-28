package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiChatLanguageModelSmokeTest extends OpenAiBaseTest {

    private static final String TOKEN = "whatever";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", TOKEN)
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void test() {
        String response = chatLanguageModel.chat("hello");
        assertThat(response).isNotBlank();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-quarkus-azure-openai");
    }
}
