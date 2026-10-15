package io.quarkiverse.langchain4j.workload.deployment;

import java.util.function.BooleanSupplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.workload.runtime.WorkloadModelAuthProviderRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.spiffe.client.SpiffeClient;
import io.vertx.core.Vertx;

@BuildSteps(onlyIf = WorkloadModelAuthProviderProcessor.IsEnabled.class)
public class WorkloadModelAuthProviderProcessor {

    private static final String FEATURE = "langchain4j-workload-model-auth-provider";
    private static final DotName MODEL_AUTH_PROVIDER = DotName.createSimple(ModelAuthProvider.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerBeans(
            WorkloadModelAuthProviderBuildConfig buildConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            WorkloadModelAuthProviderRecorder recorder) {

        var builder = SyntheticBeanBuildItem
                .configure(MODEL_AUTH_PROVIDER)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(Vertx.class)))
                .addInjectionPoint(ClassType.create(DotName.createSimple(OidcClient.class)))
                .addInjectionPoint(ParameterizedType.create(DotName.createSimple(Instance.class),
                        new Type[] { ClassType.create(DotName.createSimple(SpiffeClient.class)) }, null))
                .createWith(recorder.defaultProvider());

        if (buildConfig.modelName().isPresent()) {
            builder.addQualifier().annotation(ModelName.class)
                    .addValue("value", buildConfig.modelName().get()).done();
        }

        beanProducer.produce(builder.done());
    }

    public static class IsEnabled implements BooleanSupplier {
        WorkloadModelAuthProviderBuildConfig config;

        public boolean getAsBoolean() {
            return config.enabled().orElse(true);
        }
    }
}
