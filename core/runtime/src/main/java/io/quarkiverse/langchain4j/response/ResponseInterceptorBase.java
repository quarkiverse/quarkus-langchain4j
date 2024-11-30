package io.quarkiverse.langchain4j.response;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Simple (Chat)Response interceptor base, to be applied directly on the model.
 */
public abstract class ResponseInterceptorBase {

    private volatile String model;
    private volatile List<ResponseListener> listeners;

    // TODO -- uh uh ... reflection ... puke
    protected String getModel(Object target) {
        if (model == null) {
            try {
                Class<?> clazz = target.getClass();
                Method method = clazz.getMethod("modelName");
                model = (String) method.invoke(target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return model;
    }

    protected List<ResponseListener> getListeners() {
        if (listeners == null) {
            listeners = CDI.current().select(ResponseListener.class, Any.Literal.INSTANCE)
                    .stream()
                    .sorted(Comparator.comparing(ResponseListener::order))
                    .toList();
        }
        return listeners;
    }
}
