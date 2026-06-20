package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class InvalidOutputGuardrailAccumulatorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, DummyChatModel.class))
            .assertException(t -> {
                assertThat(t).isInstanceOf(DeploymentException.class);
                assertThat(t).hasMessageContaining(
                        "io.quarkiverse.langchain4j.test.guardrails.InvalidOutputGuardrailAccumulatorTest$MyAiService.hi");
            });

    @Test
    @ActivateRequestContext
    void testThatInvalidAccumulatorAreReported() {
        fail("Should not be called");
    }

    @RegisterAiService
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(MyGuardRail.class)
        @OutputGuardrailAccumulator(MyAccumulator.class)
        String hi(@MemoryId String mem);

    }

    @ApplicationScoped
    public static class MyAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens;
        }
    }

    @ApplicationScoped
    public static class DummyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest r) {
            return ChatResponse.builder().aiMessage(new AiMessage("")).build();
        }
    }

    @ApplicationScoped
    public static class MyGuardRail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            throw new RuntimeException("Should not be invoked");
        }

    }

}
