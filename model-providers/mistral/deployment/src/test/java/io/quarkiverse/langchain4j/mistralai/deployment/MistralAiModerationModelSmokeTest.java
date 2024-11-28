package io.quarkiverse.langchain4j.mistralai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class MistralAiModerationModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "mistral-moderation-latest";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ModerationModel moderationModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(moderationModel)).isInstanceOf(MistralAiModerationModel.class);

        wiremock().register(
                post(urlEqualTo("/v1/moderations"))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                            {
                                              "id": "32af1b4a95114a6085d054540e3b2725",
                                              "results": [
                                                {
                                                  "category_scores": {
                                                    "sexual": 0.00009608268737792969,
                                                    "hate_and_discrimination": 0.0015974044799804688,
                                                    "violence_and_threats": 0.9990234375,
                                                    "dangerous_and_criminal_content": 0.0012063980102539062,
                                                    "selfharm": 0.00017952919006347656,
                                                    "health": 0.000012218952178955078,
                                                    "financial": 0.00001895427703857422,
                                                    "law": 0.00002282857894897461,
                                                    "pii": 0.00006604194641113281
                                                  },
                                                  "categories": {
                                                    "sexual": false,
                                                    "hate_and_discrimination": false,
                                                    "violence_and_threats": true,
                                                    "dangerous_and_criminal_content": false,
                                                    "selfharm": false,
                                                    "health": false,
                                                    "financial": false,
                                                    "law": false,
                                                    "pii": false
                                                  }
                                                }
                                              ],
                                              "model": "mistral-moderation-2411"
                                            }
                                        """)));

        Moderation response = moderationModel.moderate("I will kill you!").content();
        Assertions.assertTrue(response.flagged());

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("I will kill you!").contains(CHAT_MODEL_ID);
    }

}
