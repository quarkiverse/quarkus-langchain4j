package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.ModerationException;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ModerationModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @RegisterAiService(moderationModelSupplier = RegisterAiService.BeanIfExistsModerationModelSupplier.class)
    interface ChatWithModeration {

        @Moderate
        String chat(String message);
    }

    @Inject
    ChatWithModeration chatWithModeration;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    @ActivateRequestContext
    void should_throw_when_text_is_flagged() {
        mock.moderation().reply(ModerationReply.flagged("hate", "hate/threatening"));

        assertThatThrownBy(() -> chatWithModeration.chat("I WILL KILL YOU!!!"))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"" + "I WILL KILL YOU!!!" + "\" violates content policy");

        assertModerationRequest();
    }

    @Test
    @ActivateRequestContext
    void should_not_throw_when_text_is_not_flagged() {
        mock.moderation().reply(ModerationReply.notFlagged());
        mock.chat().reply("I will hug you too");

        String result = chatWithModeration.chat("I will hug you");
        assertThat(result).isEqualTo("I will hug you too");

        assertModerationRequest();
    }

    private void assertModerationRequest() {
        CapturedRequest request = mock.moderation().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/moderations");
        assertThat(request.query()).isNull();
    }
}
