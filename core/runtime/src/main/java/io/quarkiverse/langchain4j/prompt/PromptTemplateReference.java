package io.quarkiverse.langchain4j.prompt;

import java.util.Objects;

/**
 * Identifies a prompt template stored in a registry.
 *
 * @param groupId the group of the artifact, or an empty string to use the registry's default group
 * @param artifactId the identifier of the artifact, never empty
 * @param version the version (or version expression) of the artifact, or an empty string to use the registry's default
 *        version
 */
public record PromptTemplateReference(String groupId, String artifactId, String version) {

    public PromptTemplateReference {
        groupId = groupId == null ? "" : groupId;
        version = version == null ? "" : version;
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("The artifactId of a prompt template reference must not be empty");
        }
    }

    public static PromptTemplateReference of(String artifactId) {
        return new PromptTemplateReference("", artifactId, "");
    }

    public static PromptTemplateReference of(String groupId, String artifactId, String version) {
        return new PromptTemplateReference(groupId, artifactId, version);
    }

    public boolean hasGroupId() {
        return !groupId.isEmpty();
    }

    public boolean hasVersion() {
        return !version.isEmpty();
    }

    /**
     * @return a copy of this reference where the empty group and version have been replaced by the given defaults
     */
    public PromptTemplateReference withDefaults(String defaultGroupId, String defaultVersion) {
        return new PromptTemplateReference(hasGroupId() ? groupId : Objects.requireNonNullElse(defaultGroupId, ""),
                artifactId, hasVersion() ? version : Objects.requireNonNullElse(defaultVersion, ""));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasGroupId()) {
            sb.append(groupId).append('/');
        }
        sb.append(artifactId);
        if (hasVersion()) {
            sb.append('@').append(version);
        }
        return sb.toString();
    }
}
