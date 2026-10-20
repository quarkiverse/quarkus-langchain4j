package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolChoiceToolNotFoundTest extends WatsonxMockTestBase {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice", "required")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    ChatModel model;

    @Test
    void test() {

        mock.chat().reply(ChatReply.text("AI Response").usage(59, 47));

        assertThrows(IllegalArgumentException.class,
                () -> model.chat(ChatRequest.builder().messages(UserMessage.from("test")).build()),
                "If tool-choice is 'REQUIRED', at least one tool must be specified.");

        // the request is rejected before it is sent
        assertThat(apiRequests()).isEmpty();
    }
}
