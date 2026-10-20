package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaDecisionModelAuthProviderTest {

    // the Ollama decision model speaks the SystemOne API, which the mock serves through raw stubs
    private static final String SYSTEM_ONE_PATH = "/ollama/v1/systemone";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(OllamaClientAuthHeaderFilter.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    DecisionModel model;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void authProviderIsUsed() {
        mock.raw().on("POST", SYSTEM_ONE_PATH).reply(200, """
                {"model": "nimble", "answers": {"spam": {"type": "noul", "noul": 0.97}}}
                """);

        model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build());

        assertThat(mock.requests()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo(SYSTEM_ONE_PATH);
            assertThat(request.header("Authorization")).isEqualTo("Bearer test");
        });
    }

    @Singleton
    public static class OllamaClientAuthHeaderFilter implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer test";
        }
    }
}
