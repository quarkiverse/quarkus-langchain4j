package io.quarkiverse.langchain4j.chatscopes.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.DefaultChatRoute;
import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;
import io.quarkiverse.langchain4j.chatscopes.PerChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatRouteEventBus;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatRouteRecorder;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeDefaultMemoryIdProvider;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeInjectableContext;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeThreadContextProvider;
import io.quarkiverse.langchain4j.chatscopes.internal.InternalWireInvocationScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.InvocationScopeDefaultMemoryIdProvider;
import io.quarkiverse.langchain4j.chatscopes.internal.InvocationScopeInjectableContext;
import io.quarkiverse.langchain4j.chatscopes.internal.InvocationScopeInterceptor;
import io.quarkiverse.langchain4j.chatscopes.internal.InvocationScopeThreadContextProvider;
import io.quarkiverse.langchain4j.chatscopes.internal.PerChatScopeInjectableContext;
import io.quarkiverse.langchain4j.chatscopes.internal.RequestScopedChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.internal.SessionThreadContextProvider;
import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.ArcConfig;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ContextRegistrationPhaseBuildItem.ContextConfiguratorBuildItem;
import io.quarkus.arc.deployment.CustomScopeBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.logging.Log;
import io.quarkus.smallrye.context.deployment.spi.ThreadContextProviderBuildItem;
import io.quarkus.vertx.core.deployment.IgnoredContextLocalDataKeysBuildItem;
import io.quarkus.vertx.deployment.VertxBuildConfig;

public class ChatScopeProcessor {
    static Logger log = Logger.getLogger(ChatScopeProcessor.class);
    private static final DotName DEFAULT_CHAT_ROUTE = DotName.createSimple(DefaultChatRoute.class);
    public static final DotName CHAT_ROUTE = DotName.createSimple(ChatRoute.class.getName());
    public static final DotName CHAT_SCOPED = DotName.createSimple(ChatScoped.class.getName());
    public static final DotName INVOCATION_SCOPED = DotName.createSimple(InvocationScoped.class.getName());
    public static final DotName PER_CHAT_SCOPED = DotName.createSimple(PerChatScoped.class.getName());
    public static final DotName INVOCATION_SCOPE_DEFAULT_MEMORY_ID_PROVIDER = DotName
            .createSimple(InvocationScopeDefaultMemoryIdProvider.class.getName());
    public static final DotName CHAT_SCOPE_DEFAULT_MEMORY_ID_PROVIDER = DotName
            .createSimple(ChatScopeDefaultMemoryIdProvider.class.getName());

    @BuildStep
    public void registerScopeConfigurators(ContextRegistrationPhaseBuildItem contextRegistrationPhase,
            BuildProducer<ContextConfiguratorBuildItem> producer) {
        producer.produce(new ContextConfiguratorBuildItem(contextRegistrationPhase.getContext()
                .configure(ChatScoped.class).normal().contextClass(ChatScopeInjectableContext.class)));
        producer.produce(new ContextConfiguratorBuildItem(contextRegistrationPhase.getContext()
                .configure(PerChatScoped.class).normal().contextClass(PerChatScopeInjectableContext.class)));
        producer.produce(new ContextConfiguratorBuildItem(contextRegistrationPhase.getContext()
                .configure(InvocationScoped.class).normal().contextClass(InvocationScopeInjectableContext.class)));
    }

    @BuildStep
    public void registerScope(BuildProducer<CustomScopeBuildItem> producer) {
        producer.produce(new CustomScopeBuildItem(ChatScoped.class));
        producer.produce(new CustomScopeBuildItem(PerChatScoped.class));
        producer.produce(new CustomScopeBuildItem(InvocationScoped.class));
    }

    @BuildStep
    void registerContextPropagation(ArcConfig config,
            BuildProducer<ThreadContextProviderBuildItem> threadContextProvider) {
        if (config.contextPropagation().enabled()) {
            threadContextProvider.produce(new ThreadContextProviderBuildItem(SessionThreadContextProvider.class));
            threadContextProvider.produce(new ThreadContextProviderBuildItem(ChatScopeThreadContextProvider.class));
            threadContextProvider.produce(new ThreadContextProviderBuildItem(InvocationScopeThreadContextProvider.class));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void dontPropagateCdiContext(BuildProducer<IgnoredContextLocalDataKeysBuildItem> ignoredContextKeysProducer,
            ChatRouteRecorder recorder, VertxBuildConfig buildConfig) {
        if (buildConfig.customizeArcContext()) {
            ignoredContextKeysProducer
                    .produce(new IgnoredContextLocalDataKeysBuildItem(recorder.getIgnoredArcContextKeysSupplier()));
        }
    }

    @BuildStep
    void invocationScopeAnnotationTransformer(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        producer.produce(new AnnotationsTransformerBuildItem(new AnnotationTransformation() {
            @Override
            public void apply(TransformationContext ctx) {
                if (ctx.hasAnnotation(INVOCATION_SCOPED)) {
                    ctx.add(InternalWireInvocationScoped.class);
                }
            }
        }));
    }

    @BuildStep
    void addDefaultMemoryIdProviders(List<DeclarativeAiServiceBuildItem> services,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        for (DeclarativeAiServiceBuildItem service : services) {
            if (service.getCdiScope().equals(CHAT_SCOPED) || service.getCdiScope().equals(PER_CHAT_SCOPED)) {
                service.setDefaultMemoryIdProviderClassDotName(CHAT_SCOPE_DEFAULT_MEMORY_ID_PROVIDER);
            }
            if (service.getCdiScope().equals(INVOCATION_SCOPED)) {
                service.setDefaultMemoryIdProviderClassDotName(INVOCATION_SCOPE_DEFAULT_MEMORY_ID_PROVIDER);
            }
        }
    }

    @BuildStep
    public void collectChatRoutes(BuildProducer<ChatRouteBuildItem> chatRouteProducer,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanBuildItemBuildProducer) {
        IndexView index = combinedIndexBuildItem.getIndex();
        Collection<AnnotationInstance> funqs = index.getAnnotations(CHAT_ROUTE);
        Set<String> beans = new HashSet<>();
        boolean defaultFrameFound = false;
        log.debugf("Found %d chat routes", funqs.size());
        for (AnnotationInstance funqMethod : funqs) {
            MethodInfo method = funqMethod.target().asMethod();
            ClassInfo declaringClass = method.declaringClass();
            String className = declaringClass.name().toString();
            String methodName = method.name();
            if (Modifier.isAbstract(method.flags()) && !Modifier.isInterface(declaringClass.flags())) {
                throw new RuntimeException(
                        String.format("Method '%s' annotated with '@ChatFrame' declared in the class '%s' is abstract.",
                                methodName, className));
            }

            if (Modifier.isAbstract(declaringClass.flags()) && !Modifier.isInterface(declaringClass.flags())) {
                throw new RuntimeException(
                        String.format(
                                "@ChatFrame is not allowed within abstract classes. Method '%s' annotated with '@ChatFrame' is declared within the class '%s'.",
                                methodName, className));
            }

            if (!Modifier.isPublic(method.flags())) {
                throw new RuntimeException(
                        String.format(
                                "Method '%s' annotated with '@ChatFrame' declared in the class '%s' is not public.",
                                methodName, className));
            }
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(className).methods().build());
            if (!Modifier.isInterface(declaringClass.flags())) {
                beans.add(className);
            } else if (!declaringClass.hasAnnotation(RegisterAiService.class)) {
                ClassInfo beanClass = null;
                for (ClassInfo classInfo : index.getAllKnownImplementations(declaringClass.name())) {
                    if (Modifier.isAbstract(classInfo.flags())) {
                        continue;
                    }
                    if (beanClass != null) {
                        throw new RuntimeException(
                                String.format(
                                        "Multiple bean classes implementing interface &s that has a @ChatFrame. Only one bean class is allowed per interface.",
                                        declaringClass.name().toString()));
                    }
                    beanClass = classInfo;

                }
                if (beanClass != null) {
                    beans.add(beanClass.name().toString());
                }
            } else {
                unremovableBeanBuildItemBuildProducer
                        .produce(UnremovableBeanBuildItem.beanTypes(declaringClass.name()));
            }

            String frameName = className + "::" + methodName;
            if (funqMethod.value() != null) {
                frameName = funqMethod.value().asString();
            }

            boolean defaultFrame = method.hasAnnotation(DEFAULT_CHAT_ROUTE);
            if (defaultFrame) {
                if (defaultFrameFound) {
                    throw new RuntimeException(
                            String.format(
                                    "Multiple default chat frames found. Only one @DefaultChatFrame chat frame is allowed per deployment.",
                                    methodName, className));
                }
                defaultFrameFound = true;
            }
            log.debugf("Create build item for chat route: %s::%s", className, methodName);
            chatRouteProducer.produce(new ChatRouteBuildItem(frameName, className, methodName, defaultFrame));
        }
        if (!beans.isEmpty()) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClasses(beans)
                    .setDefaultScope(CHAT_SCOPED).setUnremovable().build());
        }
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> producer) {
        producer.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClasses(InvocationScopeInterceptor.class, RequestScopedChatRouteContext.class)
                        .setUnremovable()
                        .build());
        // removeable
        producer.produce(AdditionalBeanBuildItem.builder().addBeanClass(ChatRouteEventBus.class).build());
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void registerChatRoutes(ChatRouteRecorder recorder, RecorderContext context,
            List<ChatRouteBuildItem> chatFrameBuildItems) {
        if (chatFrameBuildItems.size() == 1) {
            Log.debugf("There is only one chat frame so setting default chat frame to %s",
                    chatFrameBuildItems.get(0).getFrameName());
            ChatRouteBuildItem chatFrame = chatFrameBuildItems.get(0);
            recorder.registerRoute(chatFrame.getFrameName(), context.classProxy(chatFrame.getClassName()),
                    chatFrame.getMethodName(), true);
        } else {
            for (ChatRouteBuildItem chatFrame : chatFrameBuildItems) {
                Log.debugv("Registering chat frame: {0}", chatFrame.getFrameName());
                if (chatFrame.isDefaultFrame()) {
                    Log.debugv("Default chat frame: {0}", chatFrame.getFrameName());
                }
                recorder.registerRoute(chatFrame.getFrameName(), context.classProxy(chatFrame.getClassName()),
                        chatFrame.getMethodName(), chatFrame.isDefaultFrame());
            }
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void setContainer(ChatRouteRecorder recorder, BeanContainerBuildItem beanContainerBuildItem) {
        recorder.setContainer(beanContainerBuildItem.getValue());
    }
}
