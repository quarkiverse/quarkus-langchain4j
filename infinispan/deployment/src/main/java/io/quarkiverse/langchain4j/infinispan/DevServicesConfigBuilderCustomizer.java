package io.quarkiverse.langchain4j.infinispan;

import java.util.Map;

import org.infinispan.commons.util.Version;
import org.infinispan.server.test.core.InfinispanContainer;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Ensure we set the Infinispan image name so users don't have to.
 * <p>
 * This just temporary until Quarkus itself moves to Infinispan 15.
 */
public class DevServicesConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(final SmallRyeConfigBuilder builder) {
        // use a priority of 50 to make sure that this is overridable by any of the standard methods
        builder.withSources(
                new PropertiesConfigSource(
                        Map.of("quarkus.infinispan-client.devservices.image-name",
                                InfinispanContainer.IMAGE_BASENAME + ":" + Version.getMajorMinor()),
                        "quarkus-langchain4j-infinispan", 50));
    }
}
