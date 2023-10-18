package io.quarkiverse.langchain4j;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.services.AiServicesFactory;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;

public class QuarkusAiServicesFactory implements AiServicesFactory {

    @Override
    public <T> QuarkusAiServices<T> create(AiServiceContext context) {
        return new QuarkusAiServices<>(context);
    }

    public static class QuarkusAiServices<T> extends dev.langchain4j.service.AiServices<T> {
        public QuarkusAiServices(AiServiceContext context) {
            super(context);
        }

        @Override
        public AiServices<T> tools(List<Object> objectsWithTools) {
            context.toolSpecifications = new ArrayList<>();
            context.toolExecutors = new HashMap<>();

            for (Object objectWithTool : objectsWithTools) {
                Class<?> clazz = objectWithTool.getClass();
                List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(clazz.getName());
                if ((methodCreateInfos == null) || methodCreateInfos.isEmpty()) {
                    continue; // this is what Langchain4j does
                }
                for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
                    String invokerClassName = methodCreateInfo.getInvokerClassName();
                    ToolSpecification toolSpecification = methodCreateInfo.getToolSpecification();
                    context.toolSpecifications.add(toolSpecification);
                    context.toolExecutors.put(toolSpecification.name(),
                            new QuarkusToolExecutor(objectWithTool, invokerClassName, methodCreateInfo.getMethodName(),
                                    methodCreateInfo.getArgumentMapperClassName()));
                }
            }

            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T build() {
            Class<?> aiServiceClass = context.aiServiceClass;
            AiServiceClassCreateInfo classCreateInfo = AiServicesRecorder.getMetadata().get(aiServiceClass.getName());
            if (classCreateInfo == null) {
                throw new RuntimeException("Quarkus was not able to determine class '" + aiServiceClass.getName()
                        + "' as an AiService at build time. Consider annotating the class with '@CreatedAware'");
            }

            performBasicValidation();

            Collection<AiServiceMethodCreateInfo> methodCreateInfos = classCreateInfo.getMethodMap().values();
            for (var methodCreateInfo : methodCreateInfos) {
                if (methodCreateInfo.isRequiresModeration() && ((context.moderationModel == null))) {
                    throw illegalConfiguration("The @Moderate annotation is present, but the moderationModel is not set up. " +
                            "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
                }
            }

            try {
                return (T) Class.forName(classCreateInfo.getImplClassName(), true, Thread.currentThread()
                        .getContextClassLoader()).getConstructor(AiServiceContext.class)
                        .newInstance(context);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create class '" + classCreateInfo.getImplClassName(), e);
            }
        }
    }

}
