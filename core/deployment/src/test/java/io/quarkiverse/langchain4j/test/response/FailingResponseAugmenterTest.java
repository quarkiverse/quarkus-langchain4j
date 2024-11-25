package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.unchecked.Unchecked;

public class FailingResponseAugmenterTest {

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

    @Test
    @ActivateRequestContext
    void testExceptionInImperativeResponseAugmenter() {
        assertThatThrownBy(() -> ai.hi())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Augmenter failed");
    }

    @Test
    @ActivateRequestContext
    void testExceptionInStreamingResponseAugmenter() {
        assertThatThrownBy(() -> ai.hiStreaming().collect().asList().await().indefinitely())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Augmenter failed");
    }

    @Test
    @ActivateRequestContext
    void testStreamingFailure() {
        assertThatThrownBy(() -> ai.hiFailureInStreaming().collect().asList().await().indefinitely())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Augmenter failed in streaming");
    }

    @ApplicationScoped
    public static class ThrowingResponseAugmenter implements AiResponseAugmenter<String> {

        @Override
        public String augment(String response, ResponseAugmenterParams params) {
            throw new RuntimeException("Augmenter failed");
        }

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            throw new RuntimeException("Augmenter failed");
        }
    }

    @ApplicationScoped
    public static class FailingResponseAugmenter implements AiResponseAugmenter<String> {

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            return stream
                    .map(Unchecked.function(s -> {
                        throw new RuntimeException("Augmenter failed in streaming");
                    }));
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class, streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ThrowingResponseAugmenter.class)
        String hi();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ThrowingResponseAugmenter.class)
        Multi<String> hiStreaming();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(FailingResponseAugmenter.class)
        Multi<String> hiFailureInStreaming();
    }
}
