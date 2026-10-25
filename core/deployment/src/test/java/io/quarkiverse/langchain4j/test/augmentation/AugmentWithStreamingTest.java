package io.quarkiverse.langchain4j.test.augmentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.AugmentWith;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.AppendingAugmentor;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.ClassLevelSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoStreamedChatModel;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoStreamedChatModelSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.FirstMethodSupplier;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class AugmentWithStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AugmentWithTestUtils.class,
                            EchoStreamedChatModelSupplier.class,
                            EchoStreamedChatModel.class,
                            AppendingAugmentor.class,
                            ClassLevelSupplier.class,
                            FirstMethodSupplier.class,
                            StreamingService.class))
            .overrideConfigKey("quarkus.langchain4j.response-schema", "false");

    @Inject
    StreamingService streamingService;

    @Test
    @ActivateRequestContext
    void methodLevelSupplierOverridesClassLevelOneOnStreamingMethod() {
        int classLevelInvocationsBefore = ClassLevelSupplier.INVOCATIONS.get();

        List<String> tokens = streamingService.overridden()
                .collect().asList().await().indefinitely();

        assertThat(tokens).containsExactly("overridden|first-method");
        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(classLevelInvocationsBefore);
    }

    @Test
    @ActivateRequestContext
    void classLevelAugmentorAppliesToUnannotatedStreamingMethod() {
        int classLevelInvocationsBefore = ClassLevelSupplier.INVOCATIONS.get();

        List<String> tokens = streamingService.plain()
                .collect().asList().await().indefinitely();

        assertThat(tokens).containsExactly("plain|class-level");
        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(classLevelInvocationsBefore + 1);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = EchoStreamedChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, retrievalAugmentor = ClassLevelSupplier.class)
    interface StreamingService {

        @UserMessage("plain")
        Multi<String> plain();

        @AugmentWith(FirstMethodSupplier.class)
        @UserMessage("overridden")
        Multi<String> overridden();
    }
}
