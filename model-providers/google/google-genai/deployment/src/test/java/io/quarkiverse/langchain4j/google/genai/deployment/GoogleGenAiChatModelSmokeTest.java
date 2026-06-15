package io.quarkiverse.langchain4j.google.genai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class GoogleGenAiChatModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "dummy";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.google.genai.log-requests", "true");

    @Inject
    ChatModel chatModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(GoogleGenAiChatModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1beta/models/%s:generateContent", CHAT_MODEL_ID)))
                        .withHeader("x-goog-api-key", equalTo(API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                         {
                                           "candidates": [
                                             {
                                               "content": {
                                                 "role": "model",
                                                 "parts": [
                                                   {
                                                     "text": "Nice to meet you"
                                                   }
                                                 ]
                                               },
                                               "finishReason": "STOP",
                                               "safetyRatings": [
                                                 {
                                                   "category": "HARM_CATEGORY_HATE_SPEECH",
                                                   "probability": "NEGLIGIBLE",
                                                   "probabilityScore": 0.044847902,
                                                   "severity": "HARM_SEVERITY_NEGLIGIBLE",
                                                   "severityScore": 0.05592617
                                                 }
                                               ]
                                             }
                                           ],
                                           "usageMetadata": {
                                             "promptTokenCount": 11,
                                             "candidatesTokenCount": 37,
                                             "totalTokenCount": 48
                                           }
                                         }
                                        """)));

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("hello");
    }

}
