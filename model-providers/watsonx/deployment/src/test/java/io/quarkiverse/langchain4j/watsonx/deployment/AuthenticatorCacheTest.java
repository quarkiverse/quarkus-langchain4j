package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.auth.Authenticator;

import io.quarkus.test.QuarkusUnitTest;

public class AuthenticatorCacheTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    private static Authenticator getOrCreate(URI baseUrl, String apiKey) throws Exception {
        Class<?> clazz = Class.forName("io.quarkiverse.langchain4j.watsonx.runtime.AuthenticatorCache");
        Method m = clazz.getDeclaredMethod("getOrCreateTokenGenerator", URI.class, String.class);
        m.setAccessible(true);
        return (Authenticator) m.invoke(null, baseUrl, apiKey);
    }

    @Test
    void same_base_url_and_api_key_is_cached() throws Exception {
        var baseUrl = URI.create("http://localhost:8090");
        Authenticator first = getOrCreate(baseUrl, "cached-key");
        Authenticator second = getOrCreate(baseUrl, "cached-key");
        assertThat(second).isSameAs(first);
    }

    @Test
    void same_api_key_with_different_base_url_is_not_shared() throws Exception {
        Authenticator prod = getOrCreate(URI.create("http://localhost:8090"), "shared-key");
        Authenticator staging = getOrCreate(URI.create("http://localhost:8091"), "shared-key");
        assertThat(staging).isNotSameAs(prod);
    }

    @Test
    void null_base_url_is_supported() throws Exception {
        Authenticator first = getOrCreate(null, "null-base-url-key");
        Authenticator second = getOrCreate(null, "null-base-url-key");
        assertThat(first).isNotNull();
        assertThat(second).isSameAs(first);
    }
}
