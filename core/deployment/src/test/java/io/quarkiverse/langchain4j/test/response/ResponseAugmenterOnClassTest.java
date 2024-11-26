package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class ResponseAugmenterOnClassTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ResponseAugmenterTestUtils.FakeChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class,
                            ResponseAugmenterTestUtils.UppercaseAugmenter.class,
                            ResponseAugmenterTestUtils.AppenderAugmenter.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void testThatMethodAugmenterReplaceClassAugmenter() {
        assertThat(ai.hi()).isEqualTo("HI!");
    }

    @Test
    @ActivateRequestContext
    void testThatClassAugmenterAreAppliedToImperativeMethod() {
        assertThat(ai.hiImperative()).isEqualTo("Hi! WORLD!");
    }

    @Test
    @ActivateRequestContext
    void testThatClassAugmenterAreAppliedToStreamingMethod() {
        assertThat(ai.hiStream().collect().asList().await().indefinitely())
                .containsExactly("Hi!", " ", "World!", " WONDERFUL!");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class, chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class)
    @ResponseAugmenter(ResponseAugmenterTestUtils.AppenderAugmenter.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.UppercaseAugmenter.class)
        String hi();

        @UserMessage("Say Hello World!")
        Multi<String> hiStream();

        @UserMessage("Say Hello World!")
        String hiImperative();

    }
}
