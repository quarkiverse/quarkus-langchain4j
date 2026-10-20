package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * No API key is configured: the {@link ModelAuthProvider} bean must be enough, and its token must reach the request.
 */
public class DecisionModelAuthProviderTest {

    // "dummy" is the api-key default that means no key is configured; setting it keeps the mock from filling one in
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(DummyModelAuthProvider.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "dummy");

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
        mock.decision().reply(DecisionReply.of(DecisionReply.predicate("spam", 0.97)));

        DecisionResponse response = model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.of("Is this message spam?"))
                .build());

        assertThat(response.yesNo("spam").probability()).isEqualTo(0.97);

        CapturedRequest request = mock.decision().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/decisions");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer token");
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer token";
        }
    }
}
