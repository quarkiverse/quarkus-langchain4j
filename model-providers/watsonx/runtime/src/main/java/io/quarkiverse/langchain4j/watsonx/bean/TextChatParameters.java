package io.quarkiverse.langchain4j.watsonx.bean;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatRequestParameters;

public class TextChatParameters {

    public record TextChatResponseFormat(String type) {
    };

    public record TextChatToolChoiceTool(String type, Function function) {
        public record Function(String name) {
        };

        public static TextChatToolChoiceTool of(String name) {
            return new TextChatToolChoiceTool("function", new Function(name));
        }
    };

    private String toolChoiceOption;
    private TextChatToolChoiceTool toolChoice;
    private final Double frequencyPenalty;
    private final Map<String, Integer> logitBias;
    private final Boolean logprobs;
    private final Integer topLogprobs;
    private final Integer maxTokens;
    private final Integer n;
    private final Double presencePenalty;
    private final Integer seed;
    private final List<String> stop;
    private final Double temperature;
    private final Double topP;
    private final Long timeLimit;
    private final TextChatResponseFormat responseFormat;

    public TextChatParameters(Builder builder) {
        this.toolChoiceOption = builder.toolChoiceOption;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.logitBias = builder.logitBias;
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.maxTokens = builder.maxTokens;
        this.n = builder.n;
        this.presencePenalty = builder.presencePenalty;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.timeLimit = builder.timeLimit;
        this.seed = builder.seed;
        this.stop = builder.stop;

        if (builder.toolChoice != null && !builder.toolChoice.isBlank())
            this.toolChoice = TextChatToolChoiceTool.of(builder.toolChoice);
        else
            this.toolChoice = null;

        if (builder.responseFormat != null && builder.responseFormat.equalsIgnoreCase("json_object"))
            this.responseFormat = new TextChatResponseFormat(builder.responseFormat);
        else
            this.responseFormat = null;
    }

    public static TextChatParameters convert(ChatRequestParameters parameters) {
        Builder builder = new Builder()
                .frequencyPenalty(parameters.frequencyPenalty())
                .maxTokens(parameters.maxOutputTokens())
                .presencePenalty(parameters.presencePenalty())
                .responseFormat(
                        parameters.responseFormat() != null && parameters.responseFormat().type().equals(JSON) ? "json_object"
                                : null)
                .stop(parameters.stopSequences())
                .temperature(parameters.temperature())
                .topP(parameters.topP());

        if (parameters instanceof WatsonxChatRequestParameters watsonxParameters) {
            builder.logitBias(watsonxParameters.logitBias());
            builder.logprobs(watsonxParameters.logprobs());
            builder.n(watsonxParameters.n());
            builder.seed(watsonxParameters.seed());
            builder.timeLimit(watsonxParameters.timeLimit() != null ? watsonxParameters.timeLimit().toMillis() : null);
            builder.topLogprobs(watsonxParameters.topLogprobs());

            List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();

            if ((isNull(parameters.toolChoice()) || parameters.toolChoice().equals(ToolChoice.REQUIRED))
                    && watsonxParameters.toolChoiceName() != null
                    && !watsonxParameters.toolChoiceName().isBlank()) {

                if (toolSpecifications == null || toolSpecifications.isEmpty())
                    throw new IllegalArgumentException(
                            "If tool-choice-name is set, at least one tool must be specified.");

                builder.toolChoiceOption(null);
                builder.toolChoice(toolSpecifications.stream()
                        .filter(new Predicate<ToolSpecification>() {
                            @Override
                            public boolean test(ToolSpecification toolSpecification) {
                                return toolSpecification.name()
                                        .equalsIgnoreCase(watsonxParameters.toolChoiceName());
                            }
                        })
                        .findFirst().map(ToolSpecification::name)
                        .orElseThrow(new Supplier<IllegalArgumentException>() {
                            @Override
                            public IllegalArgumentException get() {
                                String toolList = toolSpecifications.stream()
                                        .map(ToolSpecification::name)
                                        .collect(joining(",", "[", "]"));
                                return new IllegalArgumentException(
                                        "The tool with name '%s' is not available in the list of tools sent to the model. Tool lists: %s"
                                                .formatted(watsonxParameters.toolChoiceName(), toolList));
                            }
                        }));
            } else if (parameters.toolChoice() != null) {
                switch (parameters.toolChoice()) {
                    case AUTO -> builder.toolChoiceOption("auto");
                    case REQUIRED -> {

                        if (toolSpecifications == null || toolSpecifications.isEmpty())
                            throw new IllegalArgumentException(
                                    "If tool-choice is 'REQUIRED', at least one tool must be specified.");

                        builder.toolChoiceOption("required");
                    }
                }
            }
        }

        return builder.build();
    }

    public void cleanToolChoice() {
        this.toolChoiceOption = null;
        this.toolChoice = null;
    }

    public String getToolChoiceOption() {
        return toolChoiceOption;
    }

    public TextChatToolChoiceTool getToolChoice() {
        return toolChoice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public Boolean getLogprobs() {
        return logprobs;
    }

    public Integer getTopLogprobs() {
        return topLogprobs;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Integer getN() {
        return n;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Long getTimeLimit() {
        return timeLimit;
    }

    public Integer getSeed() {
        return seed;
    }

    public List<String> getStop() {
        return stop;
    }

    public TextChatResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public static class Builder {

        private String toolChoiceOption;
        private String toolChoice;
        private Double frequencyPenalty;
        private Map<String, Integer> logitBias;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer maxTokens;
        private Integer n;
        private Double presencePenalty;
        private String responseFormat;
        private Integer seed;
        private List<String> stop;
        private Double temperature;
        private Double topP;
        private Long timeLimit;

        public Builder toolChoiceOption(String toolChoiceOption) {
            this.toolChoiceOption = toolChoiceOption;
            return this;
        }

        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder n(Integer n) {
            this.n = n;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder timeLimit(Long timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public TextChatParameters build() {
            return new TextChatParameters(this);
        }
    }
}
