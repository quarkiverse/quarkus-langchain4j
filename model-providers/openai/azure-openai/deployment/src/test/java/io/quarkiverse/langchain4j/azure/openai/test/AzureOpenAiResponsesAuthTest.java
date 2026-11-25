package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * On the Responses path, a Microsoft Entra ID token from {@code ad-token} is sent in the {@code Authorization} header, and
 * no {@code api-key} header is sent.
 */
public class AzureOpenAiResponsesAuthTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.azure-openai.endpoint-style=v1
                            quarkus.langchain4j.azure-openai.chat-model.mode=responses
                            quarkus.langchain4j.azure-openai.ad-token=my-token
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void adTokenIsSentAsABearerToken() {
        mock.reset();
        mock.chat().reply("ok");

        assertThat(chatModel.chat("hi")).isEqualTo("ok");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).endsWith("/responses");
        assertThat(request.header("Authorization")).isEqualTo("Bearer my-token");
        assertThat(request.header("api-key")).isNull();
    }
}
