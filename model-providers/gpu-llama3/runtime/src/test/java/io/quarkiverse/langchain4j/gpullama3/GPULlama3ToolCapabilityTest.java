package io.quarkiverse.langchain4j.gpullama3;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.beehive.jitllm.api.ModelCapabilities;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.UnsupportedFeatureException;

/**
 * The tool-calling refusal reads only the capabilities the engine reports, so no model or device
 * is needed here.
 */
class GPULlama3ToolCapabilityTest {

    private static final ToolSpecification WEATHER = ToolSpecification.builder().name("getWeather")
            .description("Get the current weather for a city").build();

    @Test
    void aRequestWithToolsIsRefusedWhenTheModelCannotCallTools() {
        assertThatThrownBy(() -> GPULlama3BaseModel.requireToolCalling(ModelCapabilities.NONE,
                "local/Phi-3-mini-4k-instruct", List.of(WEATHER)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("local/Phi-3-mini-4k-instruct")
                .hasMessageContaining("getWeather");
    }

    @Test
    void aRequestWithoutToolsIsAcceptedWhateverTheModelSupports() {
        assertThatCode(() -> GPULlama3BaseModel.requireToolCalling(ModelCapabilities.NONE, "m", List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> GPULlama3BaseModel.requireToolCalling(ModelCapabilities.NONE, "m", null))
                .doesNotThrowAnyException();
    }

    @Test
    void aRequestWithToolsIsAcceptedWhenTheModelCanCallTools() {
        assertThatCode(() -> GPULlama3BaseModel.requireToolCalling(new ModelCapabilities(true, false), "m",
                List.of(WEATHER))).doesNotThrowAnyException();
    }
}
