package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.SearchedArtifact;

/**
 * A2A agent cards carry no input schema, so agents discovered from the registry are built as
 * untyped agents and their input keys have to be supplied explicitly, otherwise
 * {@code DefaultA2AClientBuilder#build()} rejects them and discovery silently yields nothing.
 * These tests cover the resolution order of those keys.
 */
class ApicurioAgentsRegistryInputKeysTest {

    @Test
    void usesInputKeysDeclaredByTheArtifactLabel() {
        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(null, List.of("configured"));

        assertArrayEquals(new String[] { "topic", "style" },
                registry.resolveInputKeys(artifactWithLabels(Map.of("a2a-input-keys", "topic,style"))));
    }

    @Test
    void trimsAndSkipsBlankEntriesInTheArtifactLabel() {
        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(null, List.of("configured"));

        assertArrayEquals(new String[] { "topic", "style" },
                registry.resolveInputKeys(artifactWithLabels(Map.of("a2a-input-keys", " topic , , style "))));
    }

    @Test
    void fallsBackToTheConfiguredKeysWhenTheLabelIsAbsent() {
        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(null, List.of("question", "language"));

        assertArrayEquals(new String[] { "question", "language" },
                registry.resolveInputKeys(artifactWithLabels(Map.of("a2a-agent-url", "http://localhost:9999/a2a"))));
    }

    @Test
    void fallsBackToTheConfiguredKeysWhenTheLabelIsBlank() {
        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(null, List.of("question"));

        assertArrayEquals(new String[] { "question" },
                registry.resolveInputKeys(artifactWithLabels(Map.of("a2a-input-keys", " , "))));
    }

    @Test
    void fallsBackToInputWhenNothingIsDeclaredOrConfigured() {
        ApicurioAgentsRegistry registry = new ApicurioAgentsRegistry(null);

        assertArrayEquals(new String[] { "input" }, registry.resolveInputKeys(artifactWithLabels(Map.of())));
        assertArrayEquals(new String[] { "input" }, registry.resolveInputKeys(new SearchedArtifact()));
    }

    @Test
    void fallsBackToInputWhenTheConfiguredKeysAreEmpty() {
        assertArrayEquals(new String[] { "input" },
                new ApicurioAgentsRegistry(null, List.of()).resolveInputKeys(new SearchedArtifact()));
        assertArrayEquals(new String[] { "input" },
                new ApicurioAgentsRegistry(null, null).resolveInputKeys(new SearchedArtifact()));
    }

    private static SearchedArtifact artifactWithLabels(Map<String, Object> labelData) {
        Labels labels = new Labels();
        labels.setAdditionalData(new HashMap<>(labelData));

        SearchedArtifact artifact = new SearchedArtifact();
        artifact.setGroupId("default");
        artifact.setArtifactId("some-agent");
        artifact.setLabels(labels);
        return artifact;
    }
}
