package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.passivation.JsonPassivationRecorder;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;

public class BaseJsonPassivationProcessor {

    // register for reflection every @ChatScoped class (but not interface).
    @BuildStep
    public void registerForReflection(BeanDiscoveryFinishedBuildItem reg, BuildProducer<ReflectiveHierarchyBuildItem> reflect) {
        reg.beanStream().withScope(ChatScoped.class).forEach((bean) -> {
            reflect.produce(ReflectiveHierarchyBuildItem.builder(bean.getBeanClass()).build());
            boolean hasSubclass = !bean.getBoundDecorators().isEmpty() || !bean.getBoundInterceptors().isEmpty();

            for (var decorator : bean.getBoundDecorators()) {
                reflect.produce(ReflectiveHierarchyBuildItem.builder(decorator.getBeanClass()).build());
            }

            if (hasSubclass) {
                reflect.produce(ReflectiveHierarchyBuildItem.builder(bean.getBeanClass().toString() + "_Subclass").build());
            }
        });
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void mapper(JsonPassivationRecorder json) {
        json.initMapper();
    }

}
