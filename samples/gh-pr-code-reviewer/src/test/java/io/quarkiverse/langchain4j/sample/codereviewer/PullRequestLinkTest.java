package io.quarkiverse.langchain4j.sample.codereviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PullRequestLinkTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/quarkiverse/quarkus-langchain4j/pull/2888",
            "https://github.com/quarkiverse/quarkus-langchain4j/pull/2888/files",
            "https://github.com/quarkiverse/quarkus-langchain4j/pull/2888#issuecomment-1",
            "http://www.github.com/quarkiverse/quarkus-langchain4j/pull/2888?w=1",
            "github.com/quarkiverse/quarkus-langchain4j/pull/2888",
            "  https://github.com/quarkiverse/quarkus-langchain4j/pull/2888  "
    })
    void parsesPullRequestLinks(String link) {
        assertEquals(new PullRequestLink("quarkiverse", "quarkus-langchain4j", 2888), PullRequestLink.parse(link));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/quarkiverse/quarkus-langchain4j/issues/2888",
            "https://github.com/quarkiverse/quarkus-langchain4j",
            "https://gitlab.com/quarkiverse/quarkus-langchain4j/pull/2888",
            "https://github.com/quarkiverse/quarkus-langchain4j/pull/abc"
    })
    void rejectsOtherLinks(String link) {
        assertThrows(IllegalArgumentException.class, () -> PullRequestLink.parse(link));
    }
}
