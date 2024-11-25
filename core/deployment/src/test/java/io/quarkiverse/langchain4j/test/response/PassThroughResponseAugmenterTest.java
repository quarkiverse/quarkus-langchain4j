package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class PassThroughResponseAugmenterTest {

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
    void test() {
        assertThat(ai.hi()).isEqualTo("Hi!");
        assertThat(ai.hiStreaming().collect().asList().await().indefinitely())
                .containsExactly("Hi!", " ", "World!");
    }

    @ApplicationScoped
    public static class PassThroughResponseAugmenter implements AiResponseAugmenter<String> {

    }

    @RegisterAiService(chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class, streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(PassThroughResponseAugmenter.class)
        String hi();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(PassThroughResponseAugmenter.class)
        Multi<String> hiStreaming();

    }
}
