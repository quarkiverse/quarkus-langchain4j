package io.quarkiverse.langchain4j;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.services.AiServicesFactory;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutorFactory;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.arc.Arc;

public class QuarkusAiServicesFactory implements AiServicesFactory {

    @Override
    public <T> QuarkusAiServices<T> create(AiServiceContext context) {
        if (context instanceof QuarkusAiServiceContext) {
            return new QuarkusAiServices<>(context);
        } else {
            // the context is always empty (except for the aiServiceClass) anyway and never escapes, so we can just use our own type
            return new QuarkusAiServices<>(new QuarkusAiServiceContext(context.aiServiceClass));
        }
    }

    public static class InstanceHolder {
        public static final QuarkusAiServicesFactory INSTANCE = new QuarkusAiServicesFactory();
    }

    public static class QuarkusAiServices<T> extends AiServices<T> {

        private static final Logger log = Logger.getLogger(QuarkusAiServices.class);

        private final QuarkusToolExecutorFactory toolExecutorFactory;

        public QuarkusAiServices(AiServiceContext context) {
            super(context);
            toolExecutorFactory = Arc.container().instance(QuarkusToolExecutorFactory.class).get();
        }

        @Override
        public AiServices<T> tools(List<Object> objectsWithTools) {
            context.toolSpecifications = new ArrayList<>();
            context.toolExecutors = new HashMap<>();

            for (Object objectWithTool : objectsWithTools) {
                List<ToolMethodCreateInfo> methodCreateInfos = lookup(objectWithTool);
                if ((methodCreateInfos == null) || methodCreateInfos.isEmpty()) {
                    if ((methodCreateInfos == null) || methodCreateInfos.isEmpty()) {
                        continue; // this is what Langchain4j does
                    }
                }
                for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
                    String invokerClassName = methodCreateInfo.getInvokerClassName();
                    ToolSpecification toolSpecification = methodCreateInfo.getToolSpecification();
                    context.toolSpecifications.add(toolSpecification);
                    QuarkusToolExecutor.Context executorContext = new QuarkusToolExecutor.Context(objectWithTool,
                            invokerClassName, methodCreateInfo.getMethodName(),
                            methodCreateInfo.getArgumentMapperClassName());
                    context.toolExecutors.put(toolSpecification.name(), toolExecutorFactory.create(executorContext));
                }
            }

            return this;
        }

        public AiServices<T> auditService(AuditService auditService) {
            ((QuarkusAiServiceContext) context).auditService = auditService;
            return this;
        }

        List<ToolMethodCreateInfo> lookup(Object obj) {
            Map<String, List<ToolMethodCreateInfo>> metadata = ToolsRecorder.getMetadata();
            // Fast path first.
            String className = obj.getClass().getName();
            var fast = metadata.get(className);
            if (fast != null) {
                return fast;
            }

            for (Map.Entry<String, List<ToolMethodCreateInfo>> entry : metadata.entrySet()) {
                String targetClassName = entry.getKey();
                try {
                    var targetClass = Class.forName(targetClassName, false, Thread.currentThread().getContextClassLoader());
                    if (targetClass.isAssignableFrom(obj.getClass())) {
                        metadata.put(targetClassName, entry.getValue()); // For the next lookup.
                        return entry.getValue();
                    }
                } catch (ClassNotFoundException e) {
                    log.error("Unable to load class " + targetClassName);
                }
            }
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        @Override
        public T build() {
            Class<?> aiServiceClass = context.aiServiceClass;
            AiServiceClassCreateInfo classCreateInfo = AiServicesRecorder.getMetadata().get(aiServiceClass.getName());
            if (classCreateInfo == null) {
                throw new RuntimeException("Quarkus was not able to determine class '" + aiServiceClass.getName()
                        + "' as an AiService at build time. Consider annotating the class with "
                        + "'@CreatedAware'");
            }

            performBasicValidation();

            Collection<AiServiceMethodCreateInfo> methodCreateInfos = classCreateInfo.getMethodMap().values();
            for (var methodCreateInfo : methodCreateInfos) {
                if (methodCreateInfo.isRequiresModeration() && ((context.moderationModel == null))) {
                    throw illegalConfiguration(
                            "The @Moderate annotation is present, but the moderationModel is not set up. " +
                                    "Please ensure a valid moderationModel is configured before using the @Moderate "
                                    + "annotation.");
                }
            }

            try {
                return (T) Class.forName(classCreateInfo.getImplClassName(), true, Thread.currentThread()
                        .getContextClassLoader()).getConstructor(QuarkusAiServiceContext.class)
                        .newInstance(((QuarkusAiServiceContext) context));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create class '" + classCreateInfo.getImplClassName(), e);
            }
        }
    }

}
