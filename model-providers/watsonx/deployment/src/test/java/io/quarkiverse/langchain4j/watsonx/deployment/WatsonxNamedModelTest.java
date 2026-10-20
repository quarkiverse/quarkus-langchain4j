package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class WatsonxNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.watsonx.w1.chat-model.model-name=named-model
                            quarkus.langchain4j.watsonx.w2.space-id=my-space
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.w1.chat-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.w2.chat-model.provider", "watsonx");

    @Inject
    @ModelName("w1")
    ChatModel w1;

    @Inject
    @ModelName("w2")
    ChatModel w2;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelsReachTheMock() {
        mock.reset();
        mock.chat().reply("from w1");

        assertThat(w1.chat("hi")).isEqualTo("from w1");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/text/chat");
        assertThat(r.asChat().modelName()).isEqualTo("named-model");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-iam-token");
        assertThat(r.bodyAsJson().at("/project_id").asText()).isEqualTo("mock-project");

        mock.reset();
        mock.chat().reply("from w2");

        assertThat(w2.chat("hi")).isEqualTo("from w2");

        // a configured space means no placeholder project for w2; the provider still falls back to the default
        // model's project-id, which is the placeholder, so the request names both
        CapturedRequest r2 = mock.chat().singleRequest();
        assertThat(r2.bodyAsJson().at("/space_id").asText()).isEqualTo("my-space");
        assertThat(r2.bodyAsJson().at("/project_id").asText()).isEqualTo("mock-project");
    }
}
