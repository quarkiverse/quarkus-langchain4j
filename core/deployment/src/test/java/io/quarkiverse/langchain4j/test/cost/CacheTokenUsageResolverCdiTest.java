package io.quarkiverse.langchain4j.test.cost;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageResolver;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Validates that a {@link CacheTokenUsageExtractor} contributed as a CDI bean is discovered at build time and
 * injected into {@link CacheTokenUsageResolver} through {@code @All}.
 */
class CacheTokenUsageResolverCdiTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestProviderTokenUsage.class, TestProviderCacheTokenUsageExtractor.class));

    @Inject
    CacheTokenUsageResolver resolver;

    @Test
    void discoversExtractorAndResolvesCacheTokens() {
        CacheTokenUsage result = resolver.resolve(new TestProviderTokenUsage(11, 4));

        assertThat(result).isNotNull();
        assertThat(result.cacheReadInputTokens()).isEqualTo(11);
        assertThat(result.cacheCreationInputTokens()).isEqualTo(4);
    }

    @Test
    void returnsNoneForUnsupportedTokenUsageType() {
        assertThat(resolver.resolve(new TokenUsage(10, 20))).isEqualTo(CacheTokenUsage.none());
    }

    static final class TestProviderTokenUsage extends TokenUsage {

        private final Integer cacheReadInputTokens;
        private final Integer cacheCreationInputTokens;

        TestProviderTokenUsage(Integer cacheReadInputTokens, Integer cacheCreationInputTokens) {
            super(10, 20);
            this.cacheReadInputTokens = cacheReadInputTokens;
            this.cacheCreationInputTokens = cacheCreationInputTokens;
        }

        Integer cacheReadInputTokens() {
            return cacheReadInputTokens;
        }

        Integer cacheCreationInputTokens() {
            return cacheCreationInputTokens;
        }
    }

    @Singleton
    static final class TestProviderCacheTokenUsageExtractor implements CacheTokenUsageExtractor {

        @Override
        public boolean supports(TokenUsage tokenUsage) {
            return tokenUsage instanceof TestProviderTokenUsage;
        }

        @Override
        public CacheTokenUsage extract(TokenUsage tokenUsage) {
            TestProviderTokenUsage usage = (TestProviderTokenUsage) tokenUsage;
            return new CacheTokenUsage(usage.cacheReadInputTokens(), usage.cacheCreationInputTokens());
        }
    }
}
