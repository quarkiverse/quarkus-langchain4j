package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

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

public class VertexAiGeminiNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.langchain4j.vertexai.gemini.g1.chat-model.model-id=gemini-named\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.g1.chat-model.provider", "vertexai-gemini");

    @Inject
    @ModelName("g1")
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMockWithTheMockToken() {
        mock.reset();
        mock.chat().reply("named");

        assertThat(chatModel.chat("hi")).isEqualTo("named");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo(
                "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/gemini-named:generateContent");
        assertThat(r.asChat().modelName()).isEqualTo("gemini-named");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-access-token");
    }
}
