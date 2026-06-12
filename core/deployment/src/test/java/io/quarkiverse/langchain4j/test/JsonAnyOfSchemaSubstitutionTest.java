package io.quarkiverse.langchain4j.test;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class JsonAnyOfSchemaSubstitutionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void aiServiceWithJsonSubTypesShouldBuild() {
        ai.chat("hello");
    }

    public record MyResponse(java.util.List<Block> blocks) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(TextBlock.class),
            @JsonSubTypes.Type(NumberBlock.class),
    })
    public interface Block {
    }

    @JsonTypeName("TEXT")
    public record TextBlock(String content) implements Block {
    }

    @JsonTypeName("NUMBER")
    public record NumberBlock(int value) implements Block {
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface MyAiService {
        @UserMessage("Dummy")
        MyResponse chat(String msg);
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("{\"blocks\":[]}"))
                            .build();
                }
            };
        }
    }
}
