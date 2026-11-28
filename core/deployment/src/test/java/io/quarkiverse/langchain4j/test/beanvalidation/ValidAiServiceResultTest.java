package io.quarkiverse.langchain4j.test.beanvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

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
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * With quarkus-hibernate-validator present, {@code @Valid} on an AI service method validates the result returned by
 * the model, like for any other CDI bean method.
 */
public class ValidAiServiceResultTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Svc.class, P.class, M.class, MS.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-hibernate-validator", Version.getVersion())));

    @Inject
    Svc svc;

    @Test
    @ActivateRequestContext
    void invalidResultOfValidMethodThrows() {
        assertThatThrownBy(() -> svc.person("x"))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("name");
    }

    @Test
    @ActivateRequestContext
    void resultOfMethodWithoutValidIsNotValidated() {
        assertThat(svc.unvalidated("x").name).isEmpty();
    }

    @RegisterAiService(chatLanguageModelSupplier = MS.class)
    public interface Svc {
        @Valid
        P person(@UserMessage String m);

        P unvalidated(@UserMessage String m);
    }

    public static class P {
        @NotBlank
        public String name;
    }

    public static class MS implements Supplier<ChatModel> {
        public ChatModel get() {
            return new M();
        }
    }

    public static class M implements ChatModel {
        public ChatResponse doChat(ChatRequest r) {
            return ChatResponse.builder().aiMessage(AiMessage.from("{\"name\":\"\"}")).build();
        }
    }
}
