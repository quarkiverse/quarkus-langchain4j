package io.quarkiverse.langchain4j.testing.scorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A sample for evaluation.
 *
 * @param <T> the type of the expected output.
 */
public record EvaluationSample<T>(String name, Parameters parameters, T expectedOutput, List<String> tags) {

    /**
     * Create a new builder.
     *
     * @param <O> the type of the expected output.
     * @return a new builder.
     */
    public static <O> Builder<O> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link EvaluationSample}.
     *
     * @param <O> the type of the expected output.
     */
    public static class Builder<O> {

        private String name;
        private Parameters parameters = new Parameters();
        private List<String> tags = new ArrayList<>();
        private O expectedOutput;

        /**
         * Set the name of the sample.
         *
         * @param name the name of the sample, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the parameters of the sample.
         * The parameters are the data that will be passed to the function to evaluate.
         *
         * @param parameters the parameters, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withParameters(Parameters parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Adds a parameter to the sample.
         * The parameters are the data that will be passed to the function to evaluate.
         * Order matters when using index-based parameters.
         *
         * @param parameter the parameter, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withParameter(Parameter parameter) {
            this.parameters.add(parameter);
            return this;
        }

        /**
         * Adds a {@code String} parameter to the sample.
         * This is a convenient helper method for adding unnamed string parameters.
         * The parameters are the data that will be passed to the function to evaluate.
         * Order matters when using index-based parameters.
         *
         * @param value the parameter value, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withParameter(String value) {
            return withParameter(new Parameter.UnnamedParameter(value));
        }

        /**
         * Set the expected output of the sample.
         *
         * @param expectedOutput the expected output, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withExpectedOutput(O expectedOutput) {
            this.expectedOutput = expectedOutput;
            return this;
        }

        /**
         * Set the tags of the sample.
         *
         * @param tags the tags, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withTags(List<String> tags) {
            this.tags = new ArrayList<>(tags);
            return this;
        }

        /**
         * Adds a tag to the sample.
         *
         * @param tag the tag, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        /**
         * Adds tags to the sample.
         *
         * @param tags the tags, must not be {@code null}.
         * @return this builder.
         */
        public Builder<O> withTags(String... tags) {
            return withTags(Arrays.stream(tags).toList());
        }

        /**
         * Build the sample.
         *
         * @return the sample.
         */
        public EvaluationSample<O> build() {
            if (name == null) {
                throw new IllegalArgumentException("Name must not be null");
            }
            if (parameters == null) {
                throw new IllegalArgumentException("Parameters must not be null");
            }
            if (parameters.size() == 0) {
                throw new IllegalArgumentException("Parameters must not be empty");
            }
            if (expectedOutput == null) {
                throw new IllegalArgumentException("Expected output must not be null");
            }
            return new EvaluationSample<>(name, parameters, expectedOutput, tags);
        }
    }

}
