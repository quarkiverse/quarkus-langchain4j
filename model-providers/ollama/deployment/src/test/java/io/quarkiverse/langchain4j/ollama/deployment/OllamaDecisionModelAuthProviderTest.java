package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaDecisionModelAuthProviderTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(OllamaClientAuthHeaderFilter.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Inject
    DecisionModel model;

    @Test
    void authProviderIsUsed() {
        wiremock().register(post(urlEqualTo("/v1/systemone"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"model": "nimble", "answers": {"spam": {"type": "noul", "noul": 0.97}}}
                        """)));

        model.decide(DecisionRequest.builder()
                .input("Congratulations! You won a free cruise!")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .build());

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer test");
    }

    @Singleton
    public static class OllamaClientAuthHeaderFilter implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer test";
        }
    }
}
