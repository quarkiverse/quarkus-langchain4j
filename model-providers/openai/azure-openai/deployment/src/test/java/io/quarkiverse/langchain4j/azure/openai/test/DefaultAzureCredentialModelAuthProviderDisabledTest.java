package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.azure.openai.runtime.AzureOpenAiRecorder;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultAzureCredentialModelAuthProviderDisabledTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void defaultAzureCredentialProviderBeanIsNotRegistered() {
        var instance = Arc.container().instance(ModelAuthProvider.class);
        if (instance.isAvailable()) {
            assertThat(instance.get())
                    .isNotInstanceOf(AzureOpenAiRecorder.DefaultAzureCredentialModelAuthProvider.class);
        }
    }
}