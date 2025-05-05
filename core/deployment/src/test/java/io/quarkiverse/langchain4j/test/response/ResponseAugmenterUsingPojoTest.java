package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseAugmenterUsingPojoTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void test() {
        assertThat(ai.getPerson().name()).isEqualTo("JOHN");
    }

    @Test
    @ActivateRequestContext
    void testCastIssue() {
        assertThatThrownBy(() -> ai.hello())
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("Person");
    }

    public record Person(String name) {

    }

    @RegisterAiService(chatLanguageModelSupplier = PersonChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Dummy")
        @ResponseAugmenter(UppercaseAugmenter.class)
        Person getPerson();

        @UserMessage("Dummy")
        @ResponseAugmenter(UppercaseAugmenter.class)
        String hello();

    }

    public static class PersonChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new PersonChatModel();
        }
    }

    public static class PersonChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("{\"name\":\"John\"}")).build();
        }
    }

    @ApplicationScoped
    public static class UppercaseAugmenter implements AiResponseAugmenter<Person> {

        @Override
        public Person augment(Person response, ResponseAugmenterParams params) {
            return new Person(response.name().toUpperCase());
        }

    }
}
