package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_DEPLOYMENT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_GATEWAY_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class ChatBackendConflictTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.deployment-chat-model.deployment-id",
                    DEFAULT_DEPLOYMENT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.gateway-chat-model.model-name",
                    DEFAULT_GATEWAY_CHAT_MODEL)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class))
            .assertException(t -> {
                assertThat(t.getClass().getName()).isEqualTo(ConfigValidationException.class.getName());
                assertThat(t)
                        .hasMessageContaining("quarkus.langchain4j.watsonx.deployment-chat-model.deployment-id")
                        .hasMessageContaining("quarkus.langchain4j.watsonx.gateway-chat-model.model-name")
                        .hasMessageContaining("cannot be configured at the same time");
            });

    @Inject
    ChatModel chatModel;

    @Test
    void test() {
        fail("Should not be called");
    }
}
