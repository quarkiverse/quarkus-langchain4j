package io.quarkiverse.langchain4j.openshift.ai.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Neither the required {@code base-url} nor {@code chat-model.model-id} is configured: the mock supplies both.
 */
public class OpenshiftAiRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "openshift-ai");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void textReplyWithOnlyTheMockConfiguration() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/openshift-ai/api/v1/task/text-generation");
        assertThat(r.dialect()).isEqualTo("openshift-ai");
        assertThat(r.bodyAsJson().at("/model_id").asText()).isEqualTo("mock-model");
        assertThat(r.asChat().modelName()).isEqualTo("mock-model");
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
    }

    @Test
    void errorSurfacesAsException() {
        // the client does not retry
        mock.chat().fail(500, "boom");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("status code 500");
        assertThat(mock.chat().requests()).hasSize(1);
    }
}
