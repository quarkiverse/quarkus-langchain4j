package io.quarkiverse.langchain4j.test.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class CDIScopeResponseAugmenterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            ResponseAugmenterTestUtils.FakeChatModelSupplier.class,
                            ResponseAugmenterTestUtils.FakeChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModel.class,
                            ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class));

    @Inject
    RequestScopedAiService requestScopedAiService;

    @Inject
    RequestScopedResponseAugmenter requestScopedResponseAugmenter;

    @Inject
    ApplicationScopedResponseAugmenter applicationScopedResponseAugmenter;

    @Test
    void testRequestAndApplicationScope() {
        for (int i = 1; i < 3; i++) {
            try {
                Arc.container().requestContext().activate();
                assertThat(requestScopedAiService.hiRequestScope("123")).isEqualTo("HI!");
                assertThat(requestScopedResponseAugmenter.getInvocationCount()).isEqualTo(1);
                assertThat(requestScopedAiService.hiStreamRequestScope("123").collect().asList().await().indefinitely())
                        .containsExactly("HI!", " ", "WORLD!");
                assertThat(requestScopedResponseAugmenter.getInvocationCount()).isEqualTo(2);

                assertThat(requestScopedAiService.hiApplicationScope("123")).isEqualTo("HI!");
                if (i == 1) {
                    assertThat(applicationScopedResponseAugmenter.getInvocationCount()).isEqualTo(1);
                    assertThat(requestScopedAiService.hiStreamApplicationScope("123").collect().asList().await().indefinitely())
                            .containsExactly("HI!", " ", "WORLD!");
                    assertThat(applicationScopedResponseAugmenter.getInvocationCount()).isEqualTo(2);
                } else {
                    assertThat(applicationScopedResponseAugmenter.getInvocationCount()).isEqualTo(3);
                    assertThat(requestScopedAiService.hiStreamApplicationScope("123").collect().asList().await().indefinitely())
                            .containsExactly("HI!", " ", "WORLD!");
                    assertThat(applicationScopedResponseAugmenter.getInvocationCount()).isEqualTo(4);
                }
            } finally {
                Arc.container().requestContext().deactivate();
            }
        }
    }

    @RequestScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeStreamedChatModelSupplier.class, chatLanguageModelSupplier = ResponseAugmenterTestUtils.FakeChatModelSupplier.class)
    public interface RequestScopedAiService {

        @UserMessage("Say Hello World! {id}")
        @ResponseAugmenter(RequestScopedResponseAugmenter.class)
        String hiRequestScope(@MemoryId String id);

        @UserMessage("Say Hello World! {id}")
        @ResponseAugmenter(RequestScopedResponseAugmenter.class)
        Multi<String> hiStreamRequestScope(@MemoryId String id);

        @UserMessage("Say Hello World! {id}")
        @ResponseAugmenter(ApplicationScopedResponseAugmenter.class)
        String hiApplicationScope(@MemoryId String id);

        @UserMessage("Say Hello World! {id}")
        @ResponseAugmenter(ApplicationScopedResponseAugmenter.class)
        Multi<String> hiStreamApplicationScope(@MemoryId String id);

    }

    public static class AugmenterImpl implements AiResponseAugmenter<String> {

        int count = 0;

        @Override
        public String augment(String response, ResponseAugmenterParams params) {
            count++;
            check(params);
            return response.toUpperCase();
        }

        @Override
        public Multi<String> augment(Multi<String> stream, ResponseAugmenterParams params) {
            count++;
            check(params);
            return stream.map(String::toUpperCase);
        }

        int getInvocationCount() {
            return count;
        }
    }

    @RequestScoped
    public static class RequestScopedResponseAugmenter extends AugmenterImpl {

    }

    @ApplicationScoped
    public static class ApplicationScopedResponseAugmenter extends AugmenterImpl {

    }

    private static void check(ResponseAugmenterParams params) {
        assertThat(params.memory()).isNotNull();
        assertThat(params.userMessage()).isNotNull();
        assertThat(params.userMessage().singleText()).isEqualTo("Say Hello World! 123");
        assertThat(params.userMessageTemplate()).isEqualTo("Say Hello World! {id}");
        assertThat(params.augmentationResult()).isNull();
        assertThat(params.variables()).contains(entry("id", "123"));
    }
}
