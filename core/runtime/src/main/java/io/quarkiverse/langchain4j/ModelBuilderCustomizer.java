package io.quarkiverse.langchain4j;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;

import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

/**
 * Allows for customizing a model builder.
 * A common use case is to configure models in ways that have not been anticipated by the available Quarkus configuration.
 * <p>
 * Implementations <b>must</b> be CDI beans.
 * <p>
 * Customizers without a {@link ModelName} qualifier apply to the default model configuration.
 * Use {@code @ModelName("x")} to target a specific named configuration.
 * When a named model has {@code @ModelName}-qualified customizers, those take precedence
 * and unqualified customizers are not applied for that model.
 * <p>
 * Customizers are applied in priority order (higher priority values first).
 * Override {@link #priority()} to control ordering. The default priority is {@link #DEFAULT_PRIORITY} (0).
 * <p>
 * An example could be:
 *
 * <pre>
 * &#64;ApplicationScoped
 * public class MyCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
 *     &#64;Override
 *     public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
 *         builder.seed(42);
 *     }
 * }
 * </pre>
 *
 * @param <B> the builder type to customize
 */
public interface ModelBuilderCustomizer<B> extends Comparable<ModelBuilderCustomizer<?>> {

    int MINIMUM_PRIORITY = Integer.MIN_VALUE;
    int MAXIMUM_PRIORITY = Integer.MAX_VALUE;
    int DEFAULT_PRIORITY = 0;

    void customize(B builder);

    /**
     * Defines the priority that the customizers are applied.
     * A lower integer value means that the customizer will be applied after a customizer with a higher priority.
     */
    default int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    default int compareTo(ModelBuilderCustomizer<?> o) {
        return Integer.compare(o.priority(), priority());
    }

    static <B> void applyCustomizers(Instance<ModelBuilderCustomizer<B>> allCustomizers, B builder, String configName) {
        String resolvedName = NamedConfigUtil.isDefault(configName) ? null : configName;

        Instance<ModelBuilderCustomizer<B>> instances;
        if (NamedConfigUtil.isDefault(configName)) {
            instances = allCustomizers.select(Default.Literal.INSTANCE);
        } else {
            instances = allCustomizers.select(ModelName.Literal.of(resolvedName));
        }

        List<ModelBuilderCustomizer<B>> customizers = new ArrayList<>();
        for (ModelBuilderCustomizer<B> instance : instances) {
            customizers.add(instance);
        }

        customizers.sort(null);
        for (ModelBuilderCustomizer<B> customizer : customizers) {
            customizer.customize(builder);
        }
    }
}
