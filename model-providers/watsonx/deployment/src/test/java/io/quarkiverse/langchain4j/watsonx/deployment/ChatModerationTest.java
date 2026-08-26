package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.ChatModeration;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.UserMessage;

import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import io.quarkus.test.QuarkusUnitTest;

public class ChatModerationTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.hap.input", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.hap.output", "0.9")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.hap.mask", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.pii.input", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.pii.output", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.pii.mask", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.granite-guardian.input", "0.85")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.moderations.granite-guardian.mask", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    ChatModel chatModel;

    @Test
    void check_config() {
        var moderations = langchain4jWatsonConfig.defaultConfig().chatModel().moderations().orElseThrow();

        assertEquals(0.8, moderations.hap().orElseThrow().input().getAsDouble());
        assertEquals(0.9, moderations.hap().orElseThrow().output().getAsDouble());
        assertTrue(moderations.hap().orElseThrow().mask().orElseThrow());

        assertTrue(moderations.pii().orElseThrow().input().orElseThrow());
        assertTrue(moderations.pii().orElseThrow().output().orElseThrow());
        assertFalse(moderations.pii().orElseThrow().mask().orElseThrow());

        assertEquals(0.85, moderations.graniteGuardian().orElseThrow().input().getAsDouble());
        assertTrue(moderations.graniteGuardian().orElseThrow().mask().orElseThrow());
    }

    @Test
    void chat_sends_every_detector_in_the_moderations_payload() throws Exception {

        var moderations = ChatModeration.builder()
                .hap(h -> h.input(0.8f).output(0.9f).mask(true))
                .pii(p -> p.input(true).output(true).mask(false))
                .graniteGuardian(g -> g.input(0.85f).mask(true))
                .build();

        var messages = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                UserMessage.text("UserMessage"));

        var body = TextChatRequest.builder()
                .modelId(DEFAULT_CHAT_MODEL)
                .projectId(PROJECT_ID)
                .messages(messages)
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .moderations(moderations)
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        var response = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        assertEquals("AI Response", response.aiMessage().text());
    }

    @Test
    void chat_response_exposes_moderations_and_detections_metadata() throws Exception {

        var response = """
                {
                    "id": "cmpl-15475d0dea9b4429a55843c77997f8a9",
                    "model_id": "mistralai/mistral-large",
                    "created": 1689958352,
                    "created_at": "2023-07-21T16:52:32.190Z",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "AI Response"
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "completion_tokens": 47,
                        "prompt_tokens": 59,
                        "total_tokens": 106
                    },
                    "moderations": {
                        "pii": [
                            {
                                "score": 0.8,
                                "input": false,
                                "position": {"start": 7, "end": 19},
                                "entity": "PhoneNumber",
                                "word": "357-286-5321"
                            }
                        ]
                    },
                    "detections": {
                        "output": [
                            {
                                "choice_index": 0,
                                "results": [{
                                    "detector_id": "en_syntax_rbr_pii",
                                    "detection_type": "pii",
                                    "detection": "PhoneNumber",
                                    "score": 0.8,
                                    "text": "357-286-5321",
                                    "start": 7,
                                    "end": 19
                                }]
                            }
                        ]
                    }
                }""";

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .response(response)
                .build();

        var chatResponse = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        assertEquals("AI Response", chatResponse.aiMessage().text());

        var metadata = assertInstanceOf(WatsonxChatResponseMetadata.class, chatResponse.metadata());

        assertNotNull(metadata.moderations());
        var pii = metadata.moderations().get("pii").get(0);
        assertEquals("PhoneNumber", pii.entity());
        assertEquals("357-286-5321", pii.word());

        assertNotNull(metadata.detections());
        assertEquals(1, metadata.detections().get("output").size());
    }

    @Test
    void blocked_input_throws_content_filtered_exception() throws Exception {

        var response = """
                {
                    "id": "cmpl-blocked",
                    "model_id": "mistralai/mistral-large",
                    "created": 1689958352,
                    "created_at": "2023-07-21T16:52:32.190Z",
                    "choices": [],
                    "moderations": {
                        "pii": [
                            {
                                "score": 0.95,
                                "input": true,
                                "position": {"start": 0, "end": 12},
                                "entity": "PhoneNumber",
                                "word": "555-123-4567"
                            }
                        ]
                    }
                }""";

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .response(response)
                .build();

        assertThrows(ContentFilteredException.class, () -> chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")));
    }
}
