package io.quarkiverse.langchain4j.mistralai.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

class MistralAiModerationModelSmokeTest {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "mistral-moderation-latest";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.log-requests", "true");

    @Inject
    ModerationModel moderationModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(moderationModel)).isInstanceOf(MistralAiModerationModel.class);

        mock.moderation().reply(ModerationReply.flagged("violence_and_threats"));

        Moderation response = moderationModel.moderate("I will kill you!").content();
        Assertions.assertTrue(response.flagged());

        CapturedRequest loggedRequest = mock.moderation().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/mistralai/v1/moderations");
        assertThat(loggedRequest.query()).isNull();
        assertThat(loggedRequest.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(loggedRequest.header("User-Agent")).isEqualTo("Quarkus REST Client");
        assertThat(loggedRequest.body()).contains("I will kill you!").contains(CHAT_MODEL_ID);
    }

}
