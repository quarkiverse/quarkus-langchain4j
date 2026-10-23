package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.search.SearchRequestBuilder;
import io.apicurio.registry.rest.client.search.artifacts.ArtifactsRequestBuilder;

/**
 * Unit tests for how many search requests {@link ApicurioAgentsRegistry#allAgents()} issues to page
 * through the full result set, and how a name collision between two artifacts is reported. Both
 * happen before any agent is built. The agent URLs point at a closed local port, so building an
 * agent fails; these tests don't assert on built agents (see ApicurioRegistryA2ADiscoveryTest).
 */
class ApicurioAgentsRegistryPagingAndCollisionTest {

    @Test
    void pagesThroughTheFullResultSetUsingTheReportedCount() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any()))
                .thenReturn(resultsOf(3, List.of(artifact("default", "a1", "A1"), artifact("default", "a2", "A2"))))
                .thenReturn(resultsOf(3, List.of(artifact("default", "a3", "A3"))));

        new ApicurioAgentsRegistry(client, List.of("input"), 2).allAgents();

        verify(artifacts, times(2)).get(any());
    }

    @Test
    void stopsOnAShortFinalPageEvenWithoutAReliableCount() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        // The reported count is always null here; only a page shorter than the page size signals
        // the end of the result set.
        when(artifacts.get(any()))
                .thenReturn(resultsOf(null, List.of(artifact("default", "a1", "A1"), artifact("default", "a2", "A2"))))
                .thenReturn(resultsOf(null, List.of(artifact("default", "a3", "A3"))));

        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(client, List.of("input"), 2);
        registry.allAgents();

        verify(artifacts, times(2)).get(any());
    }

    @Test
    void stopsWhenTheLastPageIsExactlyFullAndTheCountIsReached() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any()))
                .thenReturn(resultsOf(4, List.of(artifact("default", "a1", "A1"), artifact("default", "a2", "A2"))))
                .thenReturn(resultsOf(4, List.of(artifact("default", "a3", "A3"), artifact("default", "a4", "A4"))))
                .thenReturn(resultsOf(4, List.of()));

        new ApicurioAgentsRegistry(client, List.of("input"), 2).allAgents();

        verify(artifacts, times(2)).get(any());
    }

    @Test
    void withoutACountAnExactlyFullLastPageNeedsOneEmptyPageToStop() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any()))
                .thenReturn(resultsOf(null, List.of(artifact("default", "a1", "A1"), artifact("default", "a2", "A2"))))
                .thenReturn(resultsOf(null, List.of(artifact("default", "a3", "A3"), artifact("default", "a4", "A4"))))
                .thenReturn(resultsOf(null, List.of()));

        new ApicurioAgentsRegistry(client, List.of("input"), 2).allAgents();

        verify(artifacts, times(3)).get(any());
    }

    @Test
    void stopsImmediatelyOnAnEmptyPage() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any())).thenReturn(resultsOf(0, List.of()));

        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(client, List.of("input"), 2);
        registry.allAgents();

        verify(artifacts, times(1)).get(any());
    }

    @Test
    void sameNameAcrossTwoPagesIsReportedAsAmbiguousNotSilentlyOverwritten() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any()))
                .thenReturn(resultsOf(2, List.of(artifact("g1", "a1", "Shared"))))
                .thenReturn(resultsOf(2, List.of(artifact("g2", "a2", "Shared"))));

        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(client, List.of("input"), 1);

        RuntimeException error = assertThrows(RuntimeException.class, () -> registry.getAgent("Shared"));
        assertTrue(error.getMessage().contains("g1/a1"), error.getMessage());
        assertTrue(error.getMessage().contains("g2/a2"), error.getMessage());
    }

    @Test
    void aNonCollidingAgentThatFailsToBuildIsNotReportedAsAmbiguous() {
        RegistryClient client = mockRegistryClient();
        ArtifactsRequestBuilder artifacts = artifactsBuilder(client);

        when(artifacts.get(any())).thenReturn(resultsOf(1, List.of(artifact("default", "a1", "Unique"))));

        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(client, List.of("input"), 10);

        RuntimeException error = assertThrows(RuntimeException.class, () -> registry.getAgent("Unique"));
        assertFalse(error.getMessage().contains("ambiguous"), error.getMessage());
        assertEquals("No agent found with name: Unique", error.getMessage());
    }

    private static RegistryClient mockRegistryClient() {
        return mock(RegistryClient.class);
    }

    private static ArtifactsRequestBuilder artifactsBuilder(RegistryClient client) {
        SearchRequestBuilder search = mock(SearchRequestBuilder.class);
        ArtifactsRequestBuilder artifacts = mock(ArtifactsRequestBuilder.class);
        when(client.search()).thenReturn(search);
        when(search.artifacts()).thenReturn(artifacts);
        return artifacts;
    }

    private static ArtifactSearchResults resultsOf(Integer count, List<SearchedArtifact> artifacts) {
        ArtifactSearchResults results = new ArtifactSearchResults();
        results.setCount(count);
        results.setArtifacts(artifacts);
        return results;
    }

    private static SearchedArtifact artifact(String groupId, String artifactId, String name) {
        Map<String, Object> labelData = new HashMap<>();
        labelData.put("a2a-agent-url", "http://127.0.0.1:1/" + groupId + "/" + artifactId);
        Labels labels = new Labels();
        labels.setAdditionalData(labelData);

        SearchedArtifact artifact = new SearchedArtifact();
        artifact.setGroupId(groupId);
        artifact.setArtifactId(artifactId);
        artifact.setName(name);
        artifact.setLabels(labels);
        return artifact;
    }
}
