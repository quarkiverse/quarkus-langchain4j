package io.quarkiverse.langchain4j.openai.test.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ProxyImageModelTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", "${quarkus.wiremock.devservices.port}");

    @Inject
    ImageModel imageModel;

    @Test
    public void shouldLoadImageModel() {

        Response<Image> response = imageModel.generate("whatever");
        assertNotNull(response);
        assertNotNull(response.content().url());

        assertThat(wiremock().getServeEvents()).hasSize(1);
    }
}
