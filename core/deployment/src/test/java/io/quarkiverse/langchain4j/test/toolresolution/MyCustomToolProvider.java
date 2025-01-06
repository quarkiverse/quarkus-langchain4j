package io.quarkiverse.langchain4j.test.toolresolution;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

@ApplicationScoped
public class MyCustomToolProvider implements ToolProvider {

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("get_booking_details")
                .description("Returns booking details")
                .build();
        ToolExecutor toolExecutor = (t, m) -> "TOOL1";
        return ToolProviderResult.builder()
                .add(toolSpecification, toolExecutor)
                .build();
    }
}
