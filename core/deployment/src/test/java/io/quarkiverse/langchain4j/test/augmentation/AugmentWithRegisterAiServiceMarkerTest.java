package io.quarkiverse.langchain4j.test.augmentation;

import static org.assertj.core.api.Fail.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.AugmentWith;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoChatModel;
import io.quarkiverse.langchain4j.test.augmentation.AugmentWithTestUtils.EchoChatModelSupplier;
import io.quarkus.test.QuarkusUnitTest;

public class AugmentWithRegisterAiServiceMarkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AugmentWithTestUtils.class,
                            EchoChatModelSupplier.class,
                            EchoChatModel.class,
                            MarkerConfiguredService.class))
            .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
            .setExpectedException(IllegalConfigurationException.class);

    @Test
    void registerAiServiceMarkerIsRejectedAtBuildTime() {
        fail("Build should have failed");
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MarkerConfiguredService {

        @AugmentWith(RegisterAiService.NoRetrievalAugmentorSupplier.class)
        @UserMessage("plain")
        String plain();
    }
}
