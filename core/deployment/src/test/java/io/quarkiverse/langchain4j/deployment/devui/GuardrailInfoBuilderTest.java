package io.quarkiverse.langchain4j.deployment.devui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.deployment.devui.LangChain4jDevUIProcessor.RawGuardrail;

class GuardrailInfoBuilderTest {

    @Test
    void emptyInputProducesEmptyList() {
        assertThat(LangChain4jDevUIProcessor.buildGuardrailInfos(List.of())).isEmpty();
    }

    @Test
    void guardrailSharedBySeveralServicesIsGroupedAndSortedByService() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.PiiGuard", "Input", "com.acme.Zeta", null, 1, null, List.of()),
                new RawGuardrail("com.acme.PiiGuard", "Input", "com.acme.Alpha", null, 1, null, List.of())));

        assertThat(infos).singleElement().satisfies(info -> {
            assertThat(info.className()).isEqualTo("com.acme.PiiGuard");
            assertThat(info.kind()).isEqualTo("Input");
            assertThat(info.usedBy()).extracting(GuardrailUsage::owner)
                    .containsExactly("com.acme.Alpha", "com.acme.Zeta");
            assertThat(info.usedBy()).allSatisfy(usage -> {
                assertThat(usage.position()).isEqualTo(1);
                assertThat(usage.method()).isNull();
                assertThat(usage.maxRetries()).isNull();
            });
        });
    }

    @Test
    void classLevelAndMethodLevelUsagesCoexistAndSortByMethod() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.PiiGuard", "Input", "com.acme.Assistant", null, 1, null, List.of()),
                new RawGuardrail("com.acme.PiiGuard", "Input", "com.acme.Assistant", "chat", 1, null, List.of())));

        assertThat(infos).singleElement().satisfies(info -> assertThat(info.usedBy())
                .extracting(GuardrailUsage::method)
                // class-level (null) sorts first, then method-level
                .containsExactly(null, "chat"));
    }

    @Test
    void classLevelGuardrailKeepsTheMethodsThatOverrideItAsExcluded() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.PiiGuard", "Input", "com.acme.Assistant", null, 1, null, List.of("chat")),
                new RawGuardrail("com.acme.OwnGuard", "Input", "com.acme.Assistant", "chat", 1, null, List.of())));

        assertThat(infos).filteredOn(info -> info.className().equals("com.acme.PiiGuard"))
                .singleElement()
                .satisfies(info -> assertThat(info.usedBy()).singleElement()
                        .satisfies(usage -> assertThat(usage.excludedMethods()).containsExactly("chat")));
    }

    @Test
    void positionReflectsChainOrderAndOutputCarriesMaxRetries() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.FirstOut", "Output", "com.acme.Assistant", "chat", 1, 3, List.of()),
                new RawGuardrail("com.acme.SecondOut", "Output", "com.acme.Assistant", "chat", 2, 3, List.of())));

        assertThat(infos).extracting(GuardrailInfo::className, GuardrailInfo::kind)
                .containsExactly(
                        // TreeMap orders by class name: FirstOut before SecondOut
                        tuple("com.acme.FirstOut", "Output"),
                        tuple("com.acme.SecondOut", "Output"));

        assertThat(infos.get(0).usedBy().get(0).position()).isEqualTo(1);
        assertThat(infos.get(0).usedBy().get(0).maxRetries()).isEqualTo(3);
        assertThat(infos.get(1).usedBy().get(0).position()).isEqualTo(2);
    }

    @Test
    void toolGuardrailsCarryToolClassAndMethodWithNoMaxRetries() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.AuthGuard", "Tool input", "com.acme.EmailTools", "sendEmail", 1, null, List.of()),
                new RawGuardrail("com.acme.PiiFilter", "Tool output", "com.acme.EmailTools", "sendEmail", 1, null, List.of())));

        assertThat(infos).extracting(GuardrailInfo::className, GuardrailInfo::kind)
                .containsExactly(
                        tuple("com.acme.AuthGuard", "Tool input"),
                        tuple("com.acme.PiiFilter", "Tool output"));
        assertThat(infos).allSatisfy(info -> assertThat(info.usedBy()).singleElement().satisfies(usage -> {
            assertThat(usage.owner()).isEqualTo("com.acme.EmailTools");
            assertThat(usage.method()).isEqualTo("sendEmail");
            assertThat(usage.maxRetries()).isNull();
        }));
    }

    @Test
    void sameGuardrailUsedAsInputAndOutputProducesTwoRowsInputFirst() {
        var infos = LangChain4jDevUIProcessor.buildGuardrailInfos(List.of(
                new RawGuardrail("com.acme.DualGuard", "Input", "com.acme.Assistant", null, 1, null, List.of()),
                new RawGuardrail("com.acme.DualGuard", "Output", "com.acme.Assistant", null, 1, 2, List.of())));

        assertThat(infos).extracting(GuardrailInfo::className, GuardrailInfo::kind)
                .containsExactly(
                        tuple("com.acme.DualGuard", "Input"),
                        tuple("com.acme.DualGuard", "Output"));
        assertThat(infos.get(0).usedBy().get(0).maxRetries()).isNull();
        assertThat(infos.get(1).usedBy().get(0).maxRetries()).isEqualTo(2);
    }
}
