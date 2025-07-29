package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkus.test.QuarkusUnitTest;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
public class QuarkusImperativeResponseAugmenterWithOutputGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ResponseAugmenterTestUtils.FakeChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeChatModel.class,
                            ResponseAugmenterTestUtils.UppercaseAugmenter.class,
                            ResponseAugmenterTestUtils.AppenderAugmenter.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void test() {
        assertThat(ai.hi()).isEqualTo("BONJOUR!");
    }

    @RegisterAiService(chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.UppercaseAugmenter.class)
        @OutputGuardrails(ChangingOutputGuardRail.class)
        String hi();

    }

    @ApplicationScoped
    public static class ChangingOutputGuardRail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("Bonjour!");
        }
    }
}
