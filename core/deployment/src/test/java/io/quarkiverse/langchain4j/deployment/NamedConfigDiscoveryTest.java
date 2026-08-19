package io.quarkiverse.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

class NamedConfigDiscoveryTest {

    private static final String PREFIX = "quarkus.langchain4j.store";

    interface BuildTimeConfig {

        @WithParentName
        DefaultStoreBuildTimeConfig defaultConfig();

        @WithParentName
        @WithDefaults
        Map<String, NamedStoreBuildTimeConfig> namedConfig();

        DevServicesConfig devservices();

        interface DefaultStoreBuildTimeConfig {
            boolean defaultStoreEnabled();

            Optional<String> datasource();
        }

        interface NamedStoreBuildTimeConfig {
            Optional<String> datasource();
        }

        interface DevServicesConfig {
            Optional<String> imageName();
        }
    }

    interface RuntimeConfig {

        @WithParentName
        StoreRuntimeConfig defaultConfig();

        @WithParentName
        @WithDefaults
        Map<String, StoreRuntimeConfig> namedConfig();

        interface StoreRuntimeConfig {
            String table();

            Optional<Integer> dimension();

            @WithName("register-vector-pg-extension")
            Boolean registerVectorPGExtension();

            MetadataConfig metadata();

            List<MetadataIndexConfig> metadataIndexes();

            interface MetadataConfig {
                String type();
            }

            interface MetadataIndexConfig {
                boolean unique();
            }
        }
    }

    private Set<String> discover(String... propertyNames) {
        return NamedConfigDiscovery.discoverNames(List.of(propertyNames), PREFIX, Set.of(),
                BuildTimeConfig.class, RuntimeConfig.class);
    }

    @Test
    void discoversStoresConfiguredWithRuntimePropertiesOnly() {
        assertThat(discover(
                PREFIX + ".default-store-enabled",
                PREFIX + ".products.table",
                PREFIX + ".products.dimension",
                PREFIX + ".documents.table"))
                .containsExactlyInAnyOrder("products", "documents");
    }

    @Test
    void ignoresUnnamedStoreProperties() {
        assertThat(discover(
                PREFIX + ".table",
                PREFIX + ".dimension",
                PREFIX + ".datasource",
                PREFIX + ".default-store-enabled")).isEmpty();
    }

    @Test
    void ignoresNestedGroupsOfTheUnnamedStore() {
        assertThat(discover(
                PREFIX + ".metadata.type",
                PREFIX + ".register-vector-pg-extension")).isEmpty();
    }

    @Test
    void ignoresIndexedCollectionsOfTheUnnamedStore() {
        assertThat(discover(PREFIX + ".metadata-indexes[0].unique")).isEmpty();
    }

    @Test
    void ignoresRootPropertiesOutsideTheUnnamedStore() {
        assertThat(discover(PREFIX + ".devservices.image-name")).isEmpty();
    }

    @Test
    void ignoresOtherPrefixes() {
        assertThat(discover(
                "quarkus.langchain4j.store-other.products.table",
                "quarkus.datasource.products.username")).isEmpty();
    }

    @Test
    void discoversNestedPropertiesOfANamedStore() {
        assertThat(discover(
                PREFIX + ".products.metadata.type",
                PREFIX + ".products.metadata-indexes[0].unique")).containsExactly("products");
    }

    @Test
    void unquotesNamedStoreKeys() {
        assertThat(discover(PREFIX + ".\"my.store\".table")).containsExactly("my.store");
    }

    @Test
    void keepsKnownNamesEvenWithoutMatchingProperties() {
        assertThat(NamedConfigDiscovery.discoverNames(List.of(PREFIX + ".products.table"), PREFIX, Set.of("documents"),
                BuildTimeConfig.class, RuntimeConfig.class))
                .containsExactly("documents", "products");
    }

}
