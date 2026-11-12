package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionModelBuilderCustomizerTest extends OpenAiBaseTest {

    @ApplicationScoped
    public static class MyCustomizer implements ModelBuilderCustomizer<OpenAiDecisionModel.OpenAiDecisionModelBuilder> {
        @Override
        public void customize(OpenAiDecisionModel.OpenAiDecisionModelBuilder builder) {
            builder.customHeaders(Map.of("X-Custom-From-Customizer", "customized"));
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    DecisionModel model;

    @BeforeEach
    void setUp() {
        resetRequests();
        resetMappings();
    }

    @Test
    void customizerIsApplied() {
        wiremock().register(post(urlEqualTo("/v1/decisions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "spam", "probability": 0.97}]}
                        """)));

        model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build());

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("X-Custom-From-Customizer")).isEqualTo("customized");
    }
}
