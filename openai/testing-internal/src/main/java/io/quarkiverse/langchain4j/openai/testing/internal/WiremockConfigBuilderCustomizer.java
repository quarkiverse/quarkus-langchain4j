package io.quarkiverse.langchain4j.openai.testing.internal;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Configure Wiremock DevService with the things needed by this test module
 */
public class WiremockConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        builder.withSources(
                new PropertiesConfigSource(Map.of(
                        "quarkus.wiremock.devservices.global-response-templating",
                        "true",
                        "quarkus.wiremock.devservices.extension-scanning-enabled",
                        "true",
                        "quarkus.wiremock.devservices.files-mapping",
                        "classpath:/openai"),
                        "quarkus-openai-testing-internal",
                        500));
    }
}
