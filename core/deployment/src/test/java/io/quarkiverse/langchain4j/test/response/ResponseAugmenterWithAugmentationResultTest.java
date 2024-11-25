package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class ResponseAugmenterWithAugmentationResultTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ResponseAugmenterTestUtils.FakeChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModel.class,
                            ResponseAugmenterTestUtils.UppercaseAugmenter.class,
                            ResponseAugmenterTestUtils.AppenderAugmenter.class));

    @Inject
    MyAiService ai;

    @Test
    @ActivateRequestContext
    void test() {
        assertThat(ai.hi()).isEqualTo("HI!");

        List<String> list = ai.stream().collect().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("HI!", " ", "WORLD!");
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class, chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class, retrievalAugmentor = MyRetrievalAugmentor.class)
    public interface MyAiService {

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(UppercaseAugmenter.class)
        String hi();

        @UserMessage("Say Hello World!")
        @ResponseAugmenter(UppercaseAugmenter.class)
        Multi<String> stream();

    }

    @ApplicationScoped
    public static class UppercaseAugmenter implements AiResponseAugmenter<String> {

        @Override
        public String augment(String response, ResponseAugmenterParams responseAugmenterParams) {
            assertThat(responseAugmenterParams.augmentationResult().contents()).hasSize(2);
            return response.toUpperCase();
        }

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            assertThat(params.augmentationResult().contents()).hasSize(2);
            return stream.map(String::toUpperCase);
        }
    }

    public static class MyRetrievalAugmentor implements Supplier<RetrievalAugmentor> {
        @Override
        public RetrievalAugmentor get() {
            return new RetrievalAugmentor() {
                @Override
                public dev.langchain4j.data.message.UserMessage augment(dev.langchain4j.data.message.UserMessage userMessage,
                        Metadata metadata) {
                    AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);
                    return (dev.langchain4j.data.message.UserMessage) augment(augmentationRequest).chatMessage();
                }

                @Override
                public AugmentationResult augment(AugmentationRequest augmentationRequest) {
                    List<Content> content = List.of(new Content("content1"), new Content("content2"));
                    return new AugmentationResult(dev.langchain4j.data.message.UserMessage.userMessage("augmented"), content);
                }
            };
        }
    }

}
