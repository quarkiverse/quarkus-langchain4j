package io.quarkiverse.langchain4j.guardrails;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.guardrails.InputGuardrailTest;
import io.quarkus.test.QuarkusUnitTest;

public class AiServiceMethodWithGuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AiServiceMethodWithGuardrail.class,
                            EverSuccessGuardrail.class, MyChatModel.class));

    @Inject
    AiServiceMethodWithGuardrail aiServiceMethodWithGuardrail;

    @Test
    void shouldLogWarnWhenAiServiceMethodWithGuardrail() {

        SoftAssertions.assertSoftly(softAssertions -> {
            unitTest.assertLogRecords(logRecords -> {
                List<LogRecord> logs = logRecords.stream().filter(l -> l.getLevel() == Level.WARNING)
                        .filter(l -> l.getMessage().contains("AiServiceMethodWithGuardrail#name"))
                        .toList();

                softAssertions.assertThat(logs).isNotEmpty();
                List<String> messages = logs.stream().map(LogRecord::getMessage).toList();
                softAssertions.assertThat(messages).anyMatch(m -> m.contains("AiServiceMethodWithGuardrail#name"));
                softAssertions.assertThat(messages).noneMatch(m -> m.contains("AiServiceMethodWithGuardrail#message"));
            });
        });

    }

    @RegisterAiService(chatLanguageModelSupplier = InputGuardrailTest.MyChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface AiServiceMethodWithGuardrail {

        @ToolInputGuardrails(value = {
                EverSuccessGuardrail.class
        })
        @UserMessage("""
                Hello, my name is Matheus Cruz!

                What is my name?
                """)
        String name();

        @ToolInputGuardrails(value = {
                EverSuccessGuardrail.class
        })
        @Tool("Book approver")
        String bookApprover();
    }

    @ApplicationScoped
    public static class EverSuccessGuardrail implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }
}
