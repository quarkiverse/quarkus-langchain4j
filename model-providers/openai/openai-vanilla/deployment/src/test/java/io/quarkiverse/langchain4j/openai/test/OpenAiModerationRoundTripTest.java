package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiModerationRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.moderation-model.provider", "openai");

    @Inject
    ModerationModel moderationModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void notFlaggedByDefault() {
        assertThat(moderationModel.moderate("x").content().flagged()).isFalse();
    }

    @Test
    void flaggedReply() {
        mock.moderation().reply(ModerationReply.flagged("violence"));

        assertThat(moderationModel.moderate("x").content().flagged()).isTrue();
        assertThat(mock.moderation().singleRequest().path()).endsWith("/v1/moderations");
    }
}
