package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
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
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
public class QuarkusStreamingResponseAugmenterWithOutputGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ResponseAugmenterTestUtils.FakeStreamedChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class,
                            ResponseAugmenterTestUtils.StreamedUppercaseAugmenter.class,
                            ResponseAugmenterTestUtils.AppenderAugmenter.class));

    @Inject
    MyAiService ai;

    @Inject
    OkGuardrail gr;

    @Test
    @ActivateRequestContext
    void test() {
        List<String> list = ai.hi().collect().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("HI!  ", "WORLD!");
        assertThat(gr.isCalled()).isTrue();
    }

    @Test
    @ActivateRequestContext
    void testWithAnAugmenterHandlingBothStreamingAndImperative() {
        List<String> list = ai.hiAppend().collect().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("Hi!  ", "World!", " WONDERFUL!");
        assertThat(gr.isCalled()).isTrue();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.StreamedUppercaseAugmenter.class)
        @OutputGuardrailAccumulator(MyAccumulator.class)
        @OutputGuardrails(OkGuardrail.class)
        Multi<String> hi();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.AppenderAugmenter.class)
        @OutputGuardrailAccumulator(MyAccumulator.class)
        @OutputGuardrails(OkGuardrail.class)
        Multi<String> hiAppend();

    }

    @ApplicationScoped
    public static class MyAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens.group().intoLists().of(2)
                    .map(l -> String.join(" ", l));
        }
    }

    @RequestScoped
    public static class OkGuardrail implements OutputGuardrail {

        volatile boolean called = false;

        public boolean isCalled() {
            return called;
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            called = true;
            return success();
        }
    }
}
