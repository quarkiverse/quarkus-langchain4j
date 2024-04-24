package io.quarkiverse.langchain4j.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

public class MethodUtilTest {

    private Index index;

    @BeforeEach
    public void setUp() throws IOException {
        index = Index.of(Factory.class, AiFactory.class);
    }

    @Test
    public void testDuplicateMethodsInIndex() {
        // This unit test verifies that if multiple interfaces in the hierarchy have the same method signature, then all of them are processed.
        // The test is using JandexUtil.getAllSuperinterfaces() to get all superinterfaces of an interface and their methods.
        List<MethodInfo> allMethods = new ArrayList<>();
        ClassInfo iface = index.getClassByName(MethodUtilTest.AiFactory.class.getName());
        allMethods.addAll(iface.methods());
        JandexUtil.getAllSuperinterfaces(iface, index).forEach(ci -> allMethods.addAll(ci.methods()));
        assertEquals(2, allMethods.size());
    }

    @Test
    public void shouldFindMatchingMethods() {
        ClassInfo aiFactory = index.getClassByName(MethodUtilTest.AiFactory.class.getName());
        ClassInfo factory = index.getClassByName(MethodUtilTest.Factory.class.getName());
        assertTrue(MethodUtil.methodSignaturesMatch(aiFactory.methods().get(0), factory.methods().get(0)));
    }

    interface Factory {
        String create(String query);
    }

    @RegisterAiService
    interface AiFactory extends Factory {
        @SystemMessage("Super cool system message")
        String create(@UserMessage String query);
    }
}
