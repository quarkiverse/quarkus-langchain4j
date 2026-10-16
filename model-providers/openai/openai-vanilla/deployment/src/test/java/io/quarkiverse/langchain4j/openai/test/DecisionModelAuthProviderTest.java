package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * No API key is configured: the {@link ModelAuthProvider} bean must be enough, and its token must reach the request.
 */
public class DecisionModelAuthProviderTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(DummyModelAuthProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    DecisionModel model;

    @BeforeEach
    void setUp() {
        resetRequests();
        resetMappings();
    }

    @Test
    void authProviderIsUsed() {
        wiremock().register(post(urlEqualTo("/v1/decisions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"model": "gpt-6-luna", "answers": [{"type": "predicate", "name": "spam", "probability": 0.97}]}
                        """)));

        model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.of("Is this message spam?"))
                .build());

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer token");
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer token";
        }
    }
}
