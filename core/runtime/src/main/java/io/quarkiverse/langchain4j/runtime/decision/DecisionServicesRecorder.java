package io.quarkiverse.langchain4j.runtime.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.runtime.config.DecisionServiceThresholdsConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class DecisionServicesRecorder {

    private static final Logger log = Logger.getLogger(DecisionServicesRecorder.class);

    private static final TypeLiteral<Instance<ThresholdProvider>> THRESHOLD_PROVIDERS = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jConfig> runtimeConfig;

    public DecisionServicesRecorder(RuntimeValue<LangChain4jConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<QuarkusDecisionServiceContext>, QuarkusDecisionServiceContext> createContext(
            DecisionServiceCreateInfo info) {
        return new Function<>() {
            @Override
            public QuarkusDecisionServiceContext apply(SyntheticCreationalContext<QuarkusDecisionServiceContext> context) {
                Class<?> serviceClass = loadClass(info.serviceClassName());
                DecisionModel decisionModel = NamedConfigUtil.isDefault(info.decisionModelName())
                        ? context.getInjectedReference(DecisionModel.class)
                        : context.getInjectedReference(DecisionModel.class, ModelName.Literal.of(info.decisionModelName()));

                Instance<ThresholdProvider> thresholdProviders = context.getInjectedReference(THRESHOLD_PROVIDERS);
                ThresholdProvider thresholdProvider;
                if (!thresholdProviders.isUnsatisfied()) {
                    thresholdProvider = thresholdProviders.get();
                } else {
                    DecisionServiceThresholdsConfig config = runtimeConfig.getValue().decisionServices()
                            .get(serviceClass.getSimpleName());
                    thresholdProvider = config == null ? null
                            : ConfiguredThresholds.provider(config.threshold(), config.thresholdByModel());
                }

                return new QuarkusDecisionServiceContext(DecisionServiceConfig.builder()
                        .serviceInterface(serviceClass)
                        .decisionModel(decisionModel)
                        .thresholdProvider(thresholdProvider)
                        .build());
            }
        };
    }

    /**
     * Fails the startup when a threshold of a Decision Service is configured with an invalid value or for a service on
     * which it would have no effect, or when several {@link ThresholdProvider} beans exist, and warns about the keys that
     * match no Decision Service.
     *
     * @param booleanAnswers whether each Decision Service, keyed by the simple name of its interface, has a
     *        {@code boolean} answer
     */
    public void validateThresholdConfig(Map<String, Boolean> booleanAnswers) {
        Instance<ThresholdProvider> thresholdProviders = Arc.container().select(ThresholdProvider.class);
        if (!booleanAnswers.isEmpty() && thresholdProviders.isAmbiguous()) {
            List<String> beanClasses = new ArrayList<>();
            for (Instance.Handle<ThresholdProvider> handle : thresholdProviders.handles()) {
                beanClasses.add(handle.getBean().getBeanClass().getName());
            }
            throw new AmbiguousResolutionException(
                    "The Decision Services use a single ThresholdProvider bean, but several exist: " + beanClasses);
        }
        boolean thresholdProviderBean = !thresholdProviders.isUnsatisfied();
        for (Map.Entry<String, DecisionServiceThresholdsConfig> entry : runtimeConfig.getValue().decisionServices()
                .entrySet()) {
            String name = entry.getKey();
            DecisionServiceThresholdsConfig config = entry.getValue();
            String key = "quarkus.langchain4j.decision-service.\"" + name + "\"";
            ConfiguredThresholds.validate(key, config.threshold(), config.thresholdByModel());
            Boolean booleanAnswer = booleanAnswers.get(name);
            if (booleanAnswer == null) {
                log.warnf("The configuration '%s' has no effect: there is no Decision Service named '%s'", key, name);
            } else if (!booleanAnswer) {
                throw new ConfigurationException(("The configuration '%s' has no effect: the Decision Service '%s' has "
                        + "no boolean answer, so a threshold would never be used. Apply it to the probabilities of its "
                        + "YesNoAnswer, Choice or Scale answers instead").formatted(key, name));
            } else if (thresholdProviderBean) {
                throw new ConfigurationException(("The configuration '%s' has no effect: the ThresholdProvider bean "
                        + "takes precedence").formatted(key));
            }
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load the Decision Service " + className, e);
        }
    }
}
