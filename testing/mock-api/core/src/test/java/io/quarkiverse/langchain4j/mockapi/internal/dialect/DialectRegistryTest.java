package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;

class DialectRegistryTest {

    // listed first, so only the prefix rule can make the prefixed dialect win
    DialectRegistry registry = new DialectRegistry(
            List.of(new SuffixDialect("suffix", ""), new SuffixDialect("prefixed", "/prefixed"),
                    new SuffixDialect("picky", "/picky", "/messages")));

    @Test
    void prefixedDialectWinsForItsPrefixAndSeesTheStrippedPath() {
        Optional<DialectRegistry.Resolved> resolved = resolve("/prefixed/v1/chat/completions");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().dialect().id()).isEqualTo("prefixed");
        assertThat(resolved.get().match().variant()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void prefixedDialectNeverMatchesOutsideItsPrefix() {
        assertThat(resolve("/v1/chat/completions")).get().extracting(r -> r.dialect().id()).isEqualTo("suffix");
        assertThat(resolve("/prefixedother/v1/chat/completions")).get().extracting(r -> r.dialect().id())
                .isEqualTo("suffix");
    }

    @Test
    void pathUnderAPrefixIsNeverHandedToAnUnprefixedDialect() {
        assertThat(resolve("/picky/v1/chat/completions")).isEmpty();
    }

    @Test
    void unprefixedDialectGetsTheFullPath() {
        assertThat(resolve("/other/v1/chat/completions")).get().extracting(r -> r.match().variant())
                .isEqualTo("/other/v1/chat/completions");
    }

    private Optional<DialectRegistry.Resolved> resolve(String path) {
        return registry.resolve("POST", path, Map.of(), null);
    }

    /**
     * Matches any path ending in its suffix (default {@code /chat/completions}); the variant is the path it saw.
     */
    private record SuffixDialect(String id, String pathPrefix, String suffix) implements Dialect {

        SuffixDialect(String id, String pathPrefix) {
            this(id, pathPrefix, "/chat/completions");
        }

        @Override
        public Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull) {
            return path.endsWith(suffix) ? Optional.of(new Match(Operation.CHAT, false, path))
                    : Optional.empty();
        }

        @Override
        public ChatRequestView parseChat(Match m, JsonNode body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rendered render(Match m, Reply reply, CapturedRequest req) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rendered renderError(Match m, int status, String message) {
            throw new UnsupportedOperationException();
        }
    }
}
