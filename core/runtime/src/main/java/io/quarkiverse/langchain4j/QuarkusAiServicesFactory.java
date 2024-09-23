package io.quarkiverse.langchain4j;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.services.AiServicesFactory;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;

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

        public QuarkusAiServices(AiServiceContext context) {
            super(context);
        }

        @Override
        public AiServices<T> tools(List<Object> objectsWithTools) {
            context.toolSpecifications = new ArrayList<>();
            context.toolExecutors = new HashMap<>();
            ToolsRecorder.populateToolMetadata(objectsWithTools, context.toolSpecifications, context.toolExecutors);
            return this;
        }

        public AiServices<T> auditService(AuditService auditService) {
            quarkusAiServiceContext().auditService = auditService;
            return this;
        }

        public AiServices<T> chatMemorySeeder(ChatMemorySeeder chatMemorySeeder) {
            quarkusAiServiceContext().chatMemorySeeder = chatMemorySeeder;
            return this;
        }

        public AiServices<T> imageModel(ImageModel imageModel) {
            quarkusAiServiceContext().imageModel = imageModel;
            return this;
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

            Collection<AiServiceMethodCreateInfo> methodCreateInfos = classCreateInfo.methodMap().values();
            for (var methodCreateInfo : methodCreateInfos) {
                if (methodCreateInfo.isRequiresModeration() && ((context.moderationModel == null))) {
                    throw illegalConfiguration(
                            "The @Moderate annotation is present, but the moderationModel is not set up. " +
                                    "Please ensure a valid moderationModel is configured before using the @Moderate "
                                    + "annotation.");
                }
            }

            try {
                return (T) Class.forName(classCreateInfo.implClassName(), true, Thread.currentThread()
                        .getContextClassLoader()).getConstructor(QuarkusAiServiceContext.class)
                        .newInstance(quarkusAiServiceContext());
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create class '" + classCreateInfo.implClassName(), e);
            }
        }

        private QuarkusAiServiceContext quarkusAiServiceContext() {
            return (QuarkusAiServiceContext) context;
        }
    }

}
