package io.quarkiverse.langchain4j.mockapi.apptests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelApiTestProfile;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Two providers in one application: Mistral's paths look like OpenAI's, and its path prefix keeps its requests out
 * of the OpenAI dialect, so each request is journaled under its own provider.
 */
@QuarkusTest
@TestProfile(MockModelApiTestProfile.class)
class TwoProvidersTest {

    @Inject
    ChatModel openAi;

    @Inject
    @ModelName("m1")
    ChatModel mistral;

    @Inject
    MockModelServer mock;

    @Test
    void eachRequestIsJournaledWithItsOwnDialect() {
        mock.chat().reply("from openai");
        mock.chat().reply("from mistral");

        assertThat(openAi.chat("hi")).isEqualTo("from openai");
        assertThat(mistral.chat("hi")).isEqualTo("from mistral");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).dialect()).isEqualTo("openai");
        // no base URL of its own, so the OpenAI model targets the Responses API as it would in production
        assertThat(requests.get(0).path()).isEqualTo("/v1/responses");
        assertThat(requests.get(1).dialect()).isEqualTo("mistralai");
        assertThat(requests.get(1).path()).isEqualTo("/mistralai/v1/chat/completions");
    }
}
