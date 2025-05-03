package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.ai.runtime.gemini.AiGeminiChatLanguageModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class AiGeminiChatLanguageModelV1BetaSmokeTest extends WiremockAware {

    private static final String API_VERSION = "v1Beta";
    private static final String API_KEY = "dummy";
    private static final String CHAT_MODEL_ID = "gemini-1.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.api-version", API_VERSION)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.ai.gemini.log-requests", "true");

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(AiGeminiChatLanguageModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/%s/models/%s:generateContent?key=%s",
                                API_VERSION, CHAT_MODEL_ID, API_KEY)))
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
                                                 },
                                                 {
                                                   "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
                                                   "probability": "NEGLIGIBLE",
                                                   "probabilityScore": 0.18877223,
                                                   "severity": "HARM_SEVERITY_NEGLIGIBLE",
                                                   "severityScore": 0.027324531
                                                 },
                                                 {
                                                   "category": "HARM_CATEGORY_HARASSMENT",
                                                   "probability": "NEGLIGIBLE",
                                                   "probabilityScore": 0.15278918,
                                                   "severity": "HARM_SEVERITY_NEGLIGIBLE",
                                                   "severityScore": 0.045437217
                                                 },
                                                 {
                                                   "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                                                   "probability": "NEGLIGIBLE",
                                                   "probabilityScore": 0.15869519,
                                                   "severity": "HARM_SEVERITY_NEGLIGIBLE",
                                                   "severityScore": 0.036838707
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

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("hello");
    }

}
