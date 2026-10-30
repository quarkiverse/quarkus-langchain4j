package io.quarkiverse.langchain4j.test.augmentation;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.CdiBeanSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.ClassLevelSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoChatModel;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoChatModelSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.FirstMethodSupplier;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.SecondMethodSupplier;
import io.quarkus.test.QuarkusUnitTest;

public class AugmentWithTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AugmentWithTestUtils.class,
                            EchoChatModelSupplier.class,
                            EchoChatModel.class,
                            AppendingAugmentor.class,
                            ClassLevelSupplier.class,
                            FirstMethodSupplier.class,
                            SecondMethodSupplier.class,
                            CdiBeanSupplier.class,
                            ClassLevelConfiguredService.class,
                            NoClassLevelConfiguredService.class))
            .overrideConfigKey("quarkus.langchain4j.response-schema", "false");

    @Inject
    ClassLevelConfiguredService classLevelConfiguredService;

    @Inject
    NoClassLevelConfiguredService noClassLevelConfiguredService;

    @Test
    @ActivateRequestContext
    void classLevelAugmentorAppliesToUnannotatedMethod() {
        int invocationsBefore = ClassLevelSupplier.INVOCATIONS.get();

        assertThat(classLevelConfiguredService.plain()).isEqualTo("plain|class-level");

        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(invocationsBefore + 1);
    }

    @Test
    @ActivateRequestContext
    void methodLevelSupplierOverridesClassLevelOne() {
        int classLevelInvocationsBefore = ClassLevelSupplier.INVOCATIONS.get();

        assertThat(classLevelConfiguredService.overridden()).isEqualTo("overridden|first-method");

        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(classLevelInvocationsBefore);
    }

    @Test
    @ActivateRequestContext
    void differentSuppliersAreUsedOnDifferentMethods() {
        assertThat(classLevelConfiguredService.first()).isEqualTo("first|first-method");
        assertThat(classLevelConfiguredService.second()).isEqualTo("second|second-method");
    }

    @Test
    @ActivateRequestContext
    void noneDisablesAugmentationWhileSiblingMethodsStillUseTheClassLevelOne() {
        int classLevelInvocationsBefore = ClassLevelSupplier.INVOCATIONS.get();

        assertThat(classLevelConfiguredService.skipped()).isEqualTo("skipped");
        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(classLevelInvocationsBefore);

        assertThat(classLevelConfiguredService.plain()).isEqualTo("plain|class-level");
        assertThat(ClassLevelSupplier.INVOCATIONS.get()).isEqualTo(classLevelInvocationsBefore + 1);
    }

    @Test
    @ActivateRequestContext
    void methodLevelSupplierWorksWithoutClassLevelConfiguration() {
        assertThat(noClassLevelConfiguredService.plain()).isEqualTo("plain");

        assertThat(noClassLevelConfiguredService.fromCdiBeanSupplier()).isEqualTo("from-cdi-bean|cdi-bean");
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, retrievalAugmentor = ClassLevelSupplier.class)
    interface ClassLevelConfiguredService {

        @UserMessage("plain")
        String plain();

        @AugmentWith(FirstMethodSupplier.class)
        @UserMessage("overridden")
        String overridden();

        @AugmentWith(FirstMethodSupplier.class)
        @UserMessage("first")
        String first();

        @AugmentWith(SecondMethodSupplier.class)
        @UserMessage("second")
        String second();

        @AugmentWith(AugmentWith.None.class)
        @UserMessage("skipped")
        String skipped();
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface NoClassLevelConfiguredService {

        @UserMessage("plain")
        String plain();

        @AugmentWith(CdiBeanSupplier.class)
        @UserMessage("from-cdi-bean")
        String fromCdiBeanSupplier();
    }
}
