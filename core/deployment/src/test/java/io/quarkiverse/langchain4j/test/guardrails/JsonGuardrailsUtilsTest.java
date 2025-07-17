package io.quarkiverse.langchain4j.test.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;

import io.quarkiverse.langchain4j.guardrails.JsonGuardrailsUtils;
import io.quarkus.test.QuarkusUnitTest;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
class JsonGuardrailsUtilsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    JsonGuardrailsUtils jsonGuardrailsUtils;

    record Person(String firstName, String lastName, int age) {
    }

    @Test
    public void testJsonMapExtraction() {
        String input = "Here is some text before the JSON part: {\"key\": \"value\", \"nested\": {\"innerKey\": 42}} and some text after.";
        String json = jsonGuardrailsUtils.trimNonJson(input);
        assertEquals("{\"key\": \"value\", \"nested\": {\"innerKey\": 42}}", json);
    }

    @Test
    public void testJsonListExtraction() {
        String input = "Here is some text before the JSON part: [{\"key\": \"value\", \"nested\": {\"innerKey\": 42}}, {\"key\": \"value\", \"nested\": {\"innerKey\": 42}}] and some text after.";
        String json = jsonGuardrailsUtils.trimNonJson(input);
        assertEquals(
                "[{\"key\": \"value\", \"nested\": {\"innerKey\": 42}}, {\"key\": \"value\", \"nested\": {\"innerKey\": 42}}]",
                json);
    }

    @Test
    public void testJsonValidation() {
        String input = "{\"firstName\": \"Mario\", \"lastName\": \"Fusco\", \"age\": 50} Mario turned 50 a few days ago.";
        String json = jsonGuardrailsUtils.trimNonJson(input);
        Person person = jsonGuardrailsUtils.deserialize(json, Person.class);
        assertEquals("Mario", person.firstName);
        assertEquals("Fusco", person.lastName);
        assertEquals(50, person.age);
    }

    @Test
    public void testJsonListValidation() {
        String input = "Let me introduce you [{\"firstName\": \"Mario\", \"lastName\": \"Fusco\", \"age\": 50}, {\"firstName\": \"Sofia\", \"lastName\": \"Fusco\", \"age\": 13}] Mario and his daughter.";
        String json = jsonGuardrailsUtils.trimNonJson(input);
        List<Person> family = jsonGuardrailsUtils.deserialize(json, new TypeReference<>() {
        });

        Person dad = family.get(0);
        assertEquals("Mario", dad.firstName);
        assertEquals("Fusco", dad.lastName);
        assertEquals(50, dad.age);

        Person daughter = family.get(1);
        assertEquals("Sofia", daughter.firstName);
        assertEquals("Fusco", daughter.lastName);
        assertEquals(13, daughter.age);
    }
}
