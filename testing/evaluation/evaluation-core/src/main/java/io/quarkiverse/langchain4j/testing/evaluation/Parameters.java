package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A list of parameters.
 * These are parameter passed to the function to evaluate.
 * Parameters can be named or not.
 */
public class Parameters implements Iterable<Object> {

    /**
     * Create a new set of unnamed parameters with the given values.
     *
     * @param values the values, must not be {@code null}, must not be empty.
     * @return the parameters, never {@code null}
     */
    public static Parameters of(Object... values) {
        if (values == null) {
            throw new IllegalArgumentException("Values must not be null");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Values must not be empty");
        }
        Parameters parameters = new Parameters();
        for (Object value : values) {
            parameters.parameters.add(new Parameter.UnnamedParameter(value));
        }
        return parameters;
    }

    private final List<Parameter> parameters = new CopyOnWriteArrayList<>();

    /**
     * The number of parameters.
     */
    public int size() {
        return parameters.size();
    }

    /**
     * Get the parameter at the given index.
     * The index is the position of the parameter in the list.
     * It can be either a named or an unnamed parameter.
     *
     * @param index the index, must be greater or equal to 0 and less than {@link #size()}
     * @param <T> the type of the expected value
     * @return the value at the given index
     */
    public <T> T get(int index) {
        return parameters.get(index).cast();
    }

    /**
     * Get the parameter at the given index.
     * The index is the position of the parameter in the list.
     * It can be either a named or an unnamed parameter.
     *
     * @param index the index, must be greater or equal to 0 and less than {@link #size()}
     * @param clazz the type of the expected value
     * @param <T> the type of the expected value
     * @return the value at the given index
     */
    public <T> T get(int index, Class<T> clazz) {
        return parameters.get(index).as(clazz);
    }

    /**
     * Get the named parameter using its name.
     *
     * @param name the name of the parameter, must not be {@code null}
     * @return the value, can be {@code null} if the value is {@code null}.
     * @param <T> the type of the expected value
     * @throws IllegalArgumentException if the parameter is not found
     */
    public <T> T get(String name) {
        for (Parameter parameter : parameters) {
            if (parameter instanceof Parameter.NamedParameter namedParam && namedParam.name().equals(name)) {
                return namedParam.cast();
            }
        }
        throw new IllegalArgumentException("Parameter not found: " + name);
    }

    /**
     * Get the named parameter using its name.
     *
     * @param name the name of the parameter, must not be {@code null}
     * @param clazz the type of the expected value, must not be {@code null}
     * @return the value, can be {@code null} if the value is {@code null}.
     * @param <T> the type of the expected value
     * @throws IllegalArgumentException if the parameter is not found
     */
    public <T> T get(String name, Class<T> clazz) {
        for (Parameter parameter : parameters) {
            if (parameter instanceof Parameter.NamedParameter namedParameter && namedParameter.name().equals(name)) {
                return namedParameter.as(clazz);
            }
        }
        throw new IllegalArgumentException("Parameter not found: " + name);
    }

    /**
     * Get the iterator over the parameter values.
     *
     * @return the iterator, never {@code null}
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public Iterator<Object> iterator() {
        return parameters.stream().map(Parameter::value).iterator();
    }

    /**
     * Get the array of parameter values.
     *
     * @return the array, never {@code null}
     */
    public Object[] toArray() {
        return parameters.stream().map(Parameter::value).toArray();
    }

    /**
     * Add a named parameter.
     *
     * @param name the name, must not be {@code null}
     * @param value the value, can be {@code null}
     * @return this builder.
     */
    public Parameters add(String name, Object value) {
        parameters.add(new Parameter.NamedParameter(name, value));
        return this;
    }

    /**
     * Add an parameter.
     *
     * @param parameter the parameter, must not be {@code null}
     * @return this builder.
     */
    public Parameters add(Parameter parameter) {
        parameters.add(parameter);
        return this;
    }

    /**
     * Add an unnamed parameter with the given value.
     *
     * @param value the value, can be {@code null}
     * @return this builder.
     */
    public Parameters add(Object value) {
        parameters.add(new Parameter.UnnamedParameter(value));
        return this;
    }
}
