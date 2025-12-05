package io.quarkiverse.langchain4j.testing.evaluation;

import org.eclipse.microprofile.config.spi.Converter;

import io.smallrye.config.Converters;

/**
 * Represents a parameter passed to the function to evaluate.
 */
public interface Parameter {
    /**
     * Get the value of the parameter.
     *
     * @return the value of the parameter, can be {@code null}.
     */
    Object value();

    /**
     * Get the value of the parameter and convert it to the given type.
     * <p>
     * If the value is already an instance of the given type, it is returned as is.
     * Otherwise, the value is converted to the given type using a converter (from {@link Converters}).
     *
     * @param clazz the class of the expected value, must not be {@code null}
     * @param <T> the type of the expected value
     * @return the value of the parameter converted to the given type
     * @throws IllegalArgumentException if the value cannot be converted to the given type
     */
    default <T> T as(Class<T> clazz) {
        if (clazz.isInstance(value())) {
            return clazz.cast(value());
        } else {
            Converter<T> converter = Converters.getImplicitConverter(clazz);
            if (converter != null) {
                return converter.convert(value().toString());
            } else {
                throw new ClassCastException("Cannot convert " + value() + " to " + clazz);
            }
        }
    }

    /**
     * Cast the value of the parameter to the given type.
     * <p>
     * This method is a shortcut for {@code (T) value()}.
     *
     * @param <T> the type of the expected value
     * @return the value of the parameter casted to the given type
     */
    @SuppressWarnings("unchecked")
    default <T> T cast() {
        return (T) value();
    }

    /**
     * Create a named parameter. The name is used to identify the parameter.
     *
     * @param name the name, must not be {@code null}
     * @param value the value, can be {@code null}
     */
    record NamedParameter(String name, Object value) implements Parameter {

        public NamedParameter {
            if (name == null) {
                throw new IllegalArgumentException("Name must not be null");
            }
        }
    }

    /**
     * Create an unnamed parameter.
     *
     * @param value the value, can be {@code null}
     */
    record UnnamedParameter(Object value) implements Parameter {
    }

}
