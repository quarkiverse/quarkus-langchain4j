package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class MissingResponseAugmenterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ResponseAugmenterTestUtils.FakeChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModel.class));

    @Inject
    MyAiService ai;

    @Inject
    MyAiServiceWithClassAugmenter aiWithClassAugmenter;

    @Test
    @ActivateRequestContext
    void testMissingAugmenterOnMethod() {
        assertThatThrownBy(() -> ai.hi())
                .isInstanceOf(UnsatisfiedResolutionException.class)
                .hasMessageContaining("MissingResponseAugmenter");
    }

    @Test
    @ActivateRequestContext
    void testMissingAugmenterOnClass() {
        assertThatThrownBy(() -> aiWithClassAugmenter.hi())
                .isInstanceOf(UnsatisfiedResolutionException.class)
                .hasMessageContaining("MissingResponseAugmenter");
    }

    // Not a bean.
    public static class MissingResponseAugmenter implements AiResponseAugmenter<String> {

    }

    @RegisterAiService(chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(MissingResponseAugmenter.class)
        String hi();

    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class)
    @ResponseAugmenter(MissingResponseAugmenter.class)
    public interface MyAiServiceWithClassAugmenter {

        @UserMessage("Say Hello World!")
        Multi<String> hi();

    }
}
