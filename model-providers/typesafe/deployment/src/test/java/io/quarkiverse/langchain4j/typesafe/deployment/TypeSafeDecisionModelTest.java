package io.quarkiverse.langchain4j.typesafe.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import io.quarkus.test.QuarkusUnitTest;

public class TypeSafeDecisionModelTest {

    private static final WireMockServer wireMockServer;

    private static final String RESPONSE_JSON = """
            {
              "model": "jev-latest",
              "answers": {
                "urgent": {
                  "type": "noul",
                  "noul": 0.88
                }
              },
              "usage": {
                "input_tokens": 10,
                "output_tokens": 0
              }
            }
            """;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(post(urlEqualTo("/v1/systemone"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE_JSON)));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.typesafe.base-url=http://localhost:" + wireMockServer.port() + "/\n" +
                                    "quarkus.langchain4j.typesafe.decision-model.model-name=jev-latest\n" +
                                    "quarkus.langchain4j.typesafe.api-key=test-key\n"),
                            "application.properties"));

    @Inject
    DecisionModel decisionModel;

    @Test
    void should_inject_and_execute_decision_model() {
        assertThat(decisionModel).isNotNull();

        DecisionRequest request = DecisionRequest.builder()
                .input("Customer needs urgent billing review")
                .question("urgent", YesNoQuestion.of("Is this urgent?"))
                .build();

        DecisionResponse response = decisionModel.decide(request);

        assertThat(response).isNotNull();
        assertThat(response.modelName()).isEqualTo("jev-latest");
        YesNoAnswer answer = response.yesNo("urgent");
        assertThat(answer.probability()).isEqualTo(0.88);
    }
}
