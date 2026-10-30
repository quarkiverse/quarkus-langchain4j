package io.quarkiverse.langchain4j.deployment.devui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.ToolProviderMetaBuildItem;
import io.quarkiverse.langchain4j.deployment.ToolsMetadataBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.InMemoryEmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.devui.ChatJsonRPCService;
import io.quarkiverse.langchain4j.runtime.devui.EmbeddingStoreJsonRPCService;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.tool.guardrails.ToolGuardrailAnnotationLiteral;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class LangChain4jDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage(List<DeclarativeAiServiceBuildItem> aiServices,
            ToolProviderMetaBuildItem toolProviderMetaBuildItem,
            ToolsMetadataBuildItem toolsMetadataBuildItem,
            List<EmbeddingModelProviderCandidateBuildItem> embeddingModelCandidateBuildItems,
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingModelBuildItems,
            List<EmbeddingStoreBuildItem> embeddingStoreBuildItem,
            List<SelectedChatModelProviderBuildItem> chatModelProviders,
            Optional<InMemoryEmbeddingStoreBuildItem> inMemoryEmbeddingStoreBuildItem,
            List<AdditionalDevUiCardBuildItem> additionalDevUiCardBuildItems) {
        CardPageBuildItem card = new CardPageBuildItem();
        addAiServicesPage(card, aiServices);
        addGuardrailsPage(card, aiServices, toolsMetadataBuildItem);
        if (toolsMetadataBuildItem != null) {
            addToolsPage(card, toolsMetadataBuildItem);
        }
        // For now, add the embedding store page only if
        // - there is at least one embedding model (use the default one if there's more)
        // - there is a single embedding store (in case there's more, we need a way to select
        //   it via a qualifier or something to avoid ambiguity)
        if ((!embeddingModelCandidateBuildItems.isEmpty() || !inProcessEmbeddingModelBuildItems.isEmpty()) &&
                (embeddingStoreBuildItem.size() == 1 || inMemoryEmbeddingStoreBuildItem.isPresent())) {
            addEmbeddingStorePage(card);
        }
        if (!chatModelProviders.isEmpty()) {
            addChatPage(card, aiServices);
        }

        for (AdditionalDevUiCardBuildItem additionalDevUiCardBuildItem : additionalDevUiCardBuildItems) {
            card.addPage(Page.webComponentPageBuilder()
                    .title(additionalDevUiCardBuildItem.getTitle())
                    .icon(additionalDevUiCardBuildItem.getIcon())
                    .componentLink(additionalDevUiCardBuildItem.getComponentLink()));

            additionalDevUiCardBuildItem.getBuildTimeData().forEach((k, v) -> card.addBuildTimeData(k, v));
        }

        List<ToolProviderInfo> toolProviderInfos = toolProviderMetaBuildItem.getMetadata();
        card.addBuildTimeData("toolProviders", toolProviderInfos);

        return card;
    }

    private void addEmbeddingStorePage(CardPageBuildItem card) {
        card.addPage(Page.webComponentPageBuilder().title("Embedding store")
                .componentLink("qwc-embedding-store.js")
                .icon("font-awesome-solid:database"));
    }

    private void addAiServicesPage(CardPageBuildItem card, List<DeclarativeAiServiceBuildItem> aiServices) {
        List<AiServiceInfo> infos = new ArrayList<>();
        for (DeclarativeAiServiceBuildItem aiService : aiServices) {
            List<String> tools = aiService.getToolClassInfos().stream().map(ci -> ci.name().toString()).toList();
            infos.add(new AiServiceInfo(aiService.getServiceClassInfo().name().toString(), tools));
        }

        card.addBuildTimeData("aiservices", infos);
        card.addPage(Page.webComponentPageBuilder().title("AI Services")
                .componentLink("qwc-aiservices.js")
                .staticLabel(String.valueOf(aiServices.size()))
                .icon("font-awesome-solid:robot"));
    }

    private void addGuardrailsPage(CardPageBuildItem card, List<DeclarativeAiServiceBuildItem> aiServices,
            ToolsMetadataBuildItem toolsMetadataBuildItem) {
        List<RawGuardrail> raw = new ArrayList<>();
        for (DeclarativeAiServiceBuildItem aiService : aiServices) {
            ClassInfo serviceClassInfo = aiService.getServiceClassInfo();
            String serviceName = serviceClassInfo.name().toString();
            List<MethodInfo> methods = serviceClassInfo.methods();

            // A method-level annotation overrides the class-level one of the same kind, so a class-level guardrail does
            // not run on methods that declare their own guardrails of that kind.
            List<String> inputOverrides = overridingMethods(methods, LangChain4jDotNames.INPUT_GUARDRAILS);
            List<String> outputOverrides = overridingMethods(methods, LangChain4jDotNames.OUTPUT_GUARDRAILS);

            collectGuardrails(raw, serviceName, null, serviceClassInfo.declaredAnnotation(LangChain4jDotNames.INPUT_GUARDRAILS),
                    "Input", inputOverrides);
            collectGuardrails(raw, serviceName, null,
                    serviceClassInfo.declaredAnnotation(LangChain4jDotNames.OUTPUT_GUARDRAILS),
                    "Output", outputOverrides);

            for (MethodInfo method : methods) {
                collectGuardrails(raw, serviceName, method.name(), method.annotation(LangChain4jDotNames.INPUT_GUARDRAILS),
                        "Input", List.of());
                collectGuardrails(raw, serviceName, method.name(), method.annotation(LangChain4jDotNames.OUTPUT_GUARDRAILS),
                        "Output", List.of());
            }
        }

        if (toolsMetadataBuildItem != null) {
            for (Map.Entry<String, List<ToolMethodCreateInfo>> toolClassEntry : toolsMetadataBuildItem.getMetadata()
                    .entrySet()) {
                String toolClassName = toolClassEntry.getKey();
                for (ToolMethodCreateInfo tool : toolClassEntry.getValue()) {
                    collectToolGuardrails(raw, toolClassName, tool.methodName(), tool.getInputGuardrails(), "Tool input");
                    collectToolGuardrails(raw, toolClassName, tool.methodName(), tool.getOutputGuardrails(), "Tool output");
                }
            }
        }

        List<GuardrailInfo> infos = buildGuardrailInfos(raw);
        if (infos.isEmpty()) {
            return;
        }

        card.addBuildTimeData("guardrails", infos);
        card.addPage(Page.webComponentPageBuilder().title("Guardrails")
                .componentLink("qwc-guardrails.js")
                .staticLabel(String.valueOf(infos.size()))
                .icon("font-awesome-solid:shield-halved"));
    }

    private static List<String> overridingMethods(List<MethodInfo> methods, DotName kind) {
        return methods.stream()
                .filter(method -> method.annotation(kind) != null)
                .map(MethodInfo::name)
                .distinct()
                .sorted()
                .toList();
    }

    private static void collectGuardrails(List<RawGuardrail> raw, String owner, String method,
            AnnotationInstance annotation, String kind, List<String> excludedMethods) {
        if (annotation == null) {
            return;
        }
        Type[] guardrailClasses = annotation.value().asClassArray();
        Integer maxRetries = null;
        if ("Output".equals(kind)) {
            AnnotationValue maxRetriesValue = annotation.value("maxRetries");
            maxRetries = maxRetriesValue == null ? null : maxRetriesValue.asInt();
        }
        for (int i = 0; i < guardrailClasses.length; i++) {
            raw.add(new RawGuardrail(guardrailClasses[i].name().toString(), kind, owner, method, i + 1, maxRetries,
                    excludedMethods));
        }
    }

    private static void collectToolGuardrails(List<RawGuardrail> raw, String toolClass, String method,
            ToolGuardrailAnnotationLiteral<?, ?> guardrails, String kind) {
        if (guardrails == null) {
            return;
        }
        List<String> classNames = guardrails.getClassNames();
        for (int i = 0; i < classNames.size(); i++) {
            raw.add(new RawGuardrail(classNames.get(i), kind, toolClass, method, i + 1, null, List.of()));
        }
    }

    /**
     * Inverts the per-owner guardrail declarations into a list keyed by guardrail class, so the Dev UI can show, for
     * each guardrail, its kind and every AI service or tool method using it, with its position in the chain. Kept
     * package-private and free of build items/Jandex so it can be unit tested.
     */
    static List<GuardrailInfo> buildGuardrailInfos(List<RawGuardrail> raw) {
        // className -> kind -> usages
        Map<String, Map<String, List<GuardrailUsage>>> acc = new TreeMap<>();
        for (RawGuardrail guardrail : raw) {
            acc.computeIfAbsent(guardrail.guardrailClassName(), k -> new HashMap<>())
                    .computeIfAbsent(guardrail.kind(), k -> new ArrayList<>())
                    .add(new GuardrailUsage(guardrail.owner(), guardrail.method(), guardrail.position(),
                            guardrail.maxRetries(), guardrail.excludedMethods()));
        }

        Comparator<GuardrailUsage> byOwnerMethodPosition = Comparator.comparing(GuardrailUsage::owner)
                .thenComparing(usage -> usage.method() == null ? "" : usage.method())
                .thenComparingInt(GuardrailUsage::position);
        List<GuardrailInfo> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<GuardrailUsage>>> classEntry : acc.entrySet()) {
            // Stable, readable order when a guardrail class is used in more than one role.
            for (String kind : List.of("Input", "Output", "Tool input", "Tool output")) {
                List<GuardrailUsage> usages = classEntry.getValue().get(kind);
                if (usages != null) {
                    usages.sort(byOwnerMethodPosition);
                    result.add(new GuardrailInfo(classEntry.getKey(), kind, usages));
                }
            }
        }
        return result;
    }

    /**
     * A single guardrail declaration flattened out of the AI service or tool metadata, decoupled from Jandex to keep
     * {@link #buildGuardrailInfos} testable.
     */
    record RawGuardrail(String guardrailClassName, String kind, String owner, String method, int position,
            Integer maxRetries, List<String> excludedMethods) {
    }

    private void addToolsPage(CardPageBuildItem card, ToolsMetadataBuildItem metadataBuildItem) {
        List<ToolMethodInfo> infos = new ArrayList<>();
        Map<String, List<ToolMethodCreateInfo>> metadata = metadataBuildItem.getMetadata();
        for (Map.Entry<String, List<ToolMethodCreateInfo>> toolClassEntry : metadata.entrySet()) {
            for (ToolMethodCreateInfo toolMethodCreateInfo : toolClassEntry.getValue()) {
                infos.add(new ToolMethodInfo(toolClassEntry.getKey(),
                        toolMethodCreateInfo.toolSpecification().name(),
                        toolMethodCreateInfo.toolSpecification().description()));
            }
        }
        card.addBuildTimeData("tools", infos);
        card.addPage(Page.webComponentPageBuilder().title("Tools")
                .componentLink("qwc-tools.js")
                .staticLabel(String.valueOf(infos.size()))
                .icon("font-awesome-solid:toolbox"));
    }

    private void addChatPage(CardPageBuildItem card, List<DeclarativeAiServiceBuildItem> aiServices) {
        List<String> systemMessages = aiServices.stream()
                .map(s -> s.getServiceClassInfo())
                .flatMap(c -> c.annotations().stream()) //This includes method annotations
                .filter(a -> a.name().equals(LangChain4jDotNames.SYSTEM_MESSAGE))
                // TODO: remove and support 'fromResource'
                .filter(a -> a.value() != null)
                .map(a -> String.join("", a.value().asStringArray()))
                .toList();

        card.addBuildTimeData("systemMessages", systemMessages);
        card.addPage(Page.webComponentPageBuilder().title("Chat")
                .componentLink("qwc-chat.js")
                .icon("font-awesome-solid:comments"));
    }

    @BuildStep
    void jsonRpcProviders(BuildProducer<JsonRPCProvidersBuildItem> producers,
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingModelBuildItems,
            List<EmbeddingModelProviderCandidateBuildItem> embeddingModelCandidateBuildItems,
            List<EmbeddingStoreBuildItem> embeddingStoreBuildItem,
            List<ChatModelProviderCandidateBuildItem> chatModelCandidates,
            Optional<InMemoryEmbeddingStoreBuildItem> inMemoryEmbeddingStoreBuildItem) {
        if ((!embeddingModelCandidateBuildItems.isEmpty() || !inProcessEmbeddingModelBuildItems.isEmpty()) &&
                (embeddingStoreBuildItem.size() == 1 || inMemoryEmbeddingStoreBuildItem.isPresent())) {
            producers.produce(new JsonRPCProvidersBuildItem(EmbeddingStoreJsonRPCService.class));
        }
        if (!chatModelCandidates.isEmpty()) {
            producers.produce(new JsonRPCProvidersBuildItem(ChatJsonRPCService.class));
        }
    }

}
