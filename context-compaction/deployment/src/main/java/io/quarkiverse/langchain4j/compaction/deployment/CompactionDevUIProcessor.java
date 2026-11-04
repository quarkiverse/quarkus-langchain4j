package io.quarkiverse.langchain4j.compaction.deployment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.smallrye.common.annotation.Identifier;

/**
 * Adds a Dev UI card listing the compaction strategies available in the application, built-in and custom.
 */
class CompactionDevUIProcessor {

    private static final DotName STRATEGY = DotName.createSimple(CompactionStrategy.class);
    private static final DotName IDENTIFIER = DotName.createSimple(Identifier.class);

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem card(BeanDiscoveryFinishedBuildItem beans) {
        List<Map<String, String>> strategies = new ArrayList<>();
        for (BeanInfo bean : beans.beanStream().withBeanType(STRATEGY)) {
            String id = bean.getQualifiers().stream()
                    .filter(q -> q.name().equals(IDENTIFIER))
                    .map(AnnotationInstance::value)
                    .filter(Objects::nonNull)
                    .map(AnnotationValue::asString)
                    .findFirst()
                    .orElse("(no @Identifier)");
            String type = bean.getImplClazz() != null ? bean.getImplClazz().name().toString()
                    : bean.getBeanClass().toString();
            strategies.add(Map.of("id", id, "class", type,
                    "origin",
                    type.startsWith("io.quarkiverse.langchain4j.compaction.strategies.") ? "built-in" : "application"));
        }
        strategies.sort(Comparator.comparing(m -> m.get("id")));

        CardPageBuildItem card = new CardPageBuildItem();
        card.addBuildTimeData("strategies", strategies);
        card.addPage(Page.tableDataPageBuilder("Compaction strategies")
                .icon("font-awesome-solid:compress")
                .staticLabel(String.valueOf(strategies.size()))
                .buildTimeDataKey("strategies")
                .showColumn("id")
                .showColumn("origin")
                .showColumn("class"));
        return card;
    }
}
