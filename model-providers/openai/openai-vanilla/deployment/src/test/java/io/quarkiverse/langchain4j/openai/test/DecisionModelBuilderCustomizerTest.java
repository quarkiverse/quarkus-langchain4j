package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionModelBuilderCustomizerTest {

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
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    DecisionModel model;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void customizerIsApplied() {
        mock.decision().reply(DecisionReply.of(DecisionReply.predicate("spam", 0.97)));

        DecisionResponse response = model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.of("Is this message spam?"))
                .build());

        assertThat(response.yesNo("spam").probability()).isEqualTo(0.97);

        CapturedRequest request = mock.decision().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/decisions");
        assertThat(request.query()).isNull();
        assertThat(request.header("X-Custom-From-Customizer")).isEqualTo("customized");
    }
}
