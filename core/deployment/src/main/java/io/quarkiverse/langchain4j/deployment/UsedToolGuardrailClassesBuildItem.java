package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Internal build items collecting the tool guardrail classes.
 */
public final class UsedToolGuardrailClassesBuildItem extends SimpleBuildItem {

    private final List<DotName> classes;

    public UsedToolGuardrailClassesBuildItem(List<DotName> classes) {
        this.classes = classes;
    }

    public List<DotName> getClasses() {
        return classes;
    }

}
