package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Copied from {@code dev.langchain4j.model.input.PromptTemplateTest}
 * Meant to ensure that the Quarkus integration works as expected
 */
public class PromptTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void should_create_prompt_from_template_with_single_variable() {

        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{it}}.");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus.");
    }

    @Test
    void should_create_prompt_from_template_with_multiple_variables() {

        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{name}} {{surname}}.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Klaus");
        variables.put("surname", "Heißler");

        Prompt prompt = promptTemplate.apply(variables);

        assertThat(prompt.text()).isEqualTo("My name is Klaus Heißler.");
    }

    @Test
    void should_provide_date_automatically() {

        PromptTemplate promptTemplate = PromptTemplate.from("My name is {{it}} and today is {{current_date}}");

        Prompt prompt = promptTemplate.apply("Klaus");

        assertThat(prompt.text()).isEqualTo("My name is Klaus and today is " + LocalDate.now());
    }
}
