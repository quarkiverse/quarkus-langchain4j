package io.quarkiverse.langchain4j.prompt.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.prompt.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.PromptVariable;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptTemplateContentParser;

class PromptTemplateContentParserTest {

    @Test
    void parsesJsonDocument() {
        String content = """
                {
                  "templateId": "support-chat",
                  "template": "Answer the question: {{question}} in a {{tone}} tone",
                  "variables": {
                    "question": { "type": "string", "required": true, "description": "The customer question" },
                    "tone": { "type": "string", "default": "friendly" }
                  }
                }
                """;
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse(content, "1");

        assertThat(template.text()).isEqualTo("Answer the question: {{question}} in a {{tone}} tone");
        assertThat(template.version()).isEqualTo("1");
        assertThat(template.variables()).containsOnlyKeys("question", "tone");
        PromptVariable question = template.variables().get("question");
        assertThat(question.required()).isTrue();
        assertThat(question.type()).isEqualTo("string");
        assertThat(question.description()).isEqualTo("The customer question");
        PromptVariable tone = template.variables().get("tone");
        assertThat(tone.required()).isFalse();
        assertThat(tone.defaultValue()).isEqualTo("friendly");
        assertThat(template.missingRequiredVariables(List.of("tone"))).containsExactly("question");
        assertThat(template.missingRequiredVariables(List.of("question"))).isEmpty();
    }

    @Test
    void parsesYamlDocument() {
        String content = """
                templateId: sentiment-analysis
                name: Sentiment Analysis Prompt
                version: "1.0.0"
                template: |
                  Analyze the sentiment of: {{customerMessage}}
                variables:
                  customerMessage:
                    type: string
                    required: true
                """;
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse(content, null);

        assertThat(template.text()).isEqualTo("Analyze the sentiment of: {{customerMessage}}\n");
        assertThat(template.version()).isEqualTo("1.0.0");
        assertThat(template.variables().get("customerMessage").required()).isTrue();
    }

    @Test
    void parsesPromptyFrontMatterDocument() {
        String content = """
                ---
                name: Summarize
                inputs:
                  text:
                    type: string
                    required: true
                  maxWords: 50
                ---
                Summarize the following text in at most {{maxWords}} words:

                {{text}}
                """;
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse(content, "3");

        assertThat(template.text()).isEqualTo("Summarize the following text in at most {{maxWords}} words:\n\n{{text}}");
        assertThat(template.variables()).containsOnlyKeys("text", "maxWords");
        assertThat(template.variables().get("text").required()).isTrue();
        assertThat(template.variables().get("maxWords").defaultValue()).isEqualTo(50);
        assertThat(template.variables().get("maxWords").type()).isEqualTo("integer");
    }

    @Test
    void frontMatterTemplateFieldTakesPrecedenceOverBody() {
        String content = "---\ntemplate: From front matter\n---\nFrom body\n";
        assertThat(PromptTemplateContentParser.parse(content, null).text()).isEqualTo("From front matter");
    }

    @Test
    void parsesJsonSchemaStyleVariables() {
        String content = """
                {
                  "template": "Hello {{name}}, you are {{age}}",
                  "variables": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "age": { "type": "integer", "default": 42 }
                    },
                    "required": ["name"]
                  }
                }
                """;
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse(content, null);

        assertThat(template.variables()).containsOnlyKeys("name", "age");
        assertThat(template.variables().get("name").required()).isTrue();
        assertThat(template.variables().get("age").required()).isFalse();
        assertThat(template.variables().get("age").defaultValue()).isEqualTo(42);
    }

    @Test
    void treatsPlainTextAsTemplateWithoutVariables() {
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse("You are a helpful assistant for {{product}}.",
                "7");

        assertThat(template.text()).isEqualTo("You are a helpful assistant for {{product}}.");
        assertThat(template.version()).isEqualTo("7");
        assertThat(template.variables()).isEmpty();
    }

    @Test
    void rejectsStructuredContentWithoutTemplate() {
        assertThatThrownBy(() -> PromptTemplateContentParser.parse("{\"name\": \"not a prompt\"}", null))
                .isInstanceOf(PromptTemplateResolutionException.class)
                .hasMessageContaining("template");
        assertThatThrownBy(() -> PromptTemplateContentParser.parse("   ", null))
                .isInstanceOf(PromptTemplateResolutionException.class);
    }
}
