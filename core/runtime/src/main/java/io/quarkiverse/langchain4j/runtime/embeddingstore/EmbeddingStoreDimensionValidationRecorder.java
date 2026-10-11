package io.quarkiverse.langchain4j.runtime.embeddingstore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Emits a warning at startup when the dimension of the {@link EmbeddingModel} does not match the dimension a
 * registered embedding store is configured with. The matching of the embedding model dimension is best effort:
 * if no embedding model is available or it cannot be initialized, the check is skipped. In that case the
 * per-add check performed by {@link DimensionMismatchWarning} still warns when embeddings are inserted.
 */
@Recorder
public class EmbeddingStoreDimensionValidationRecorder {

    private static final Logger LOG = Logger.getLogger(EmbeddingStoreDimensionValidationRecorder.class);

    public void validateDimensions(List<EmbeddingStoreDimensionConfig> stores) {
        BeanManager beanManager = Arc.container().beanManager();
        Set<Bean<?>> beans = beanManager.getBeans(EmbeddingModel.class);
        InjectableBean<?> modelBean = beans.stream()
                .map(bean -> (InjectableBean<?>) bean)
                .filter(bean -> !isInProcessEmbeddingModel(bean.getBeanClass()))
                .max(Comparator.comparingInt(InjectableBean::getPriority))
                .orElse(null);
        if (modelBean == null) {
            // Do not instantiate in-process (ONNX) models just to read the dimension: they eagerly load native
            // libraries and the model weights, and the per-add check still warns on a mismatch.
            return;
        }
        Integer modelDimension;
        try {
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(modelBean);
            try {
                Object reference = beanManager.getReference(modelBean, EmbeddingModel.class, creationalContext);
                modelDimension = ((EmbeddingModel) reference).dimension();
            } finally {
                creationalContext.release();
            }
        } catch (RuntimeException e) {
            // the model could not be initialized, the per-add check will still warn if needed
            return;
        }
        if (modelDimension == null || modelDimension <= 0) {
            return;
        }
        for (EmbeddingStoreDimensionConfig store : stores) {
            Optional<Integer> configuredDimension = store.getDimension() != null
                    ? Optional.of(store.getDimension())
                    : ConfigProvider.getConfig().getOptionalValue(store.getDimensionConfigProperty(), Integer.class);
            if (configuredDimension.isEmpty() || configuredDimension.get().equals(modelDimension)) {
                continue;
            }
            String storeDisplayName = store.getStoreName() == null ? ""
                    : " of store '" + store.getStoreName() + "'";
            String message = ("The configured dimension (%d)%s does not match the dimension (%d) of the embedding model, "
                    + "so the embeddings might not be found during a search. "
                    + "Please check the '%s' property.")
                    .formatted(configuredDimension.get(), storeDisplayName, modelDimension,
                            store.getDimensionConfigProperty());
            LOG.warn(message);
        }
    }

    private boolean isInProcessEmbeddingModel(Class<?> beanClass) {
        return beanClass.getName().startsWith("dev.langchain4j.model.embedding.onnx.");
    }
}
