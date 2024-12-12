package io.quarkiverse.langchain4j.deployment.items;

import jakarta.enterprise.util.AnnotationLiteral;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Used to determine if a class containing a tool should be used along with a CDI qualifier
 */
public interface ToolQualifierProvider {

    boolean supports(ClassInfo classInfo);

    AnnotationLiteral<?> qualifier(ClassInfo classInfo);

    final class BuildItem extends MultiBuildItem {

        private final ToolQualifierProvider provider;

        public BuildItem(ToolQualifierProvider provider) {
            this.provider = provider;
        }

        public ToolQualifierProvider getProvider() {
            return provider;
        }
    }
}
