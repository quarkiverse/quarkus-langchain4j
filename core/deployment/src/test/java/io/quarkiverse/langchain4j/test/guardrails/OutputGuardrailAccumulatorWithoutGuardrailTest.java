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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OutputGuardrailAccumulatorWithoutGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, DummyStreamingChatModel.class))
            .assertException(t -> {
                assertThat(t).isInstanceOf(DeploymentException.class);
                assertThat(t).hasMessageContaining(
                        "io.quarkiverse.langchain4j.test.guardrails.OutputGuardrailAccumulatorWithoutGuardrailTest$MyAiService.hi");
            });

    @Test
    @ActivateRequestContext
    void testThatInvalidAccumulatorAreReported() {
        fail("Should not be called");
    }

    @RegisterAiService
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrailAccumulator(MyAccumulator.class)
        Multi<String> hi(@MemoryId String mem);

    }

    @ApplicationScoped
    public static class DummyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest r, StreamingChatResponseHandler h) {
            h.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    @ApplicationScoped
    public static class MyAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens;
        }
    }

}
