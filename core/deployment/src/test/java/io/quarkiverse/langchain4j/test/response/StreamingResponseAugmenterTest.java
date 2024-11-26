package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

public class StreamingResponseAugmenterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ResponseAugmenterTestUtils.FakeStreamedChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class,
                            ResponseAugmenterTestUtils.StreamedUppercaseAugmenter.class,
                            ResponseAugmenterTestUtils.AppenderAugmenter.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void test() {
        List<String> list = ai.hi().collect().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("HI!", " ", "WORLD!");
    }

    @Test
    @ActivateRequestContext
    void testWithAnAugmenterHandlingBothStreamingAndImperative() {
        List<String> list = ai.hiAppend().collect().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("Hi!", " ", "World!", " WONDERFUL!");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.StreamedUppercaseAugmenter.class)
        Multi<String> hi();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(ResponseAugmenterTestUtils.AppenderAugmenter.class)
        Multi<String> hiAppend();

    }
}
